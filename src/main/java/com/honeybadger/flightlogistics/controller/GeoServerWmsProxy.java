/*
 * The MIT License
 * Copyright © 2026 José Hernández
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.honeybadger.flightlogistics.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transparent proxy for GeoServer WMS GetMap requests.
 *
 * <p>Every unique tile (identified by a SHA-256 of its normalised query
 * parameters) is cached to disk on first fetch. Subsequent requests for the
 * same tile are served directly from the cache — typically 10-100x faster and
 * available even when GeoServer is offline.
 */
@RestController
@RequestMapping("/api/map")
public class GeoServerWmsProxy {

    private static final Logger log = LoggerFactory.getLogger(GeoServerWmsProxy.class);

    private final String wmsUpstreamUrl;
    private final Path   cacheDir;
    private final HttpClient http;

    /** Opens the circuit after this many consecutive upstream failures. */
    private static final int  CIRCUIT_FAILURE_THRESHOLD = 3;
    /** Duration (ms) to keep the circuit open before allowing a probe request. */
    private static final long CIRCUIT_OPEN_DURATION_MS  = 60_000L;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong    circuitOpenUntil    = new AtomicLong(0);

    /**
     * Limits concurrent upstream calls to GeoServer.
     * Threads that cannot immediately acquire a permit return 503 rather
     * than queueing up behind a slow/unreachable GeoServer.
     * 6 permits lets Leaflet's typical burst of 3 layers × ~12 tiles mostly
     * succeed on the first request without overwhelming GeoServer.
     */
    private final Semaphore upstreamPermit = new Semaphore(6);

    /**
     * Creates a new {@code GeoServerWmsProxy}.
     *
     * @param wmsUpstreamUrl upstream WMS base URL (injected from {@code geoserver.wms-url})
     * @param cacheDirPath   path to the on-disk tile cache directory
     *                       (injected from {@code map.tile-cache.dir})
     * @throws IOException if the cache directory cannot be created
     */
    public GeoServerWmsProxy(
            @Value("${geoserver.wms-url:http://127.0.0.1:8600/geoserver/wms}") String wmsUpstreamUrl,
            @Value("${map.tile-cache.dir:${user.home}/.flight-logistics/tile-cache}") String cacheDirPath)
            throws IOException {
        this.wmsUpstreamUrl = wmsUpstreamUrl;
        this.cacheDir       = Path.of(cacheDirPath);
        this.http           = HttpClient.newBuilder()
                                  .connectTimeout(Duration.ofSeconds(10))
                                  .build();
        Files.createDirectories(this.cacheDir);
        log.info("WMS tile cache directory: {}", this.cacheDir);
    }

    /**
     * Proxies a WMS {@code GetMap} request to the upstream GeoServer.
     *
     * <p>Query parameters are normalised (lowercased, sorted) and used to
     * derive a SHA-256 cache key. Tiles that have been served before are
     * returned from the local disk cache without contacting GeoServer.
     *
     * @param request incoming HTTP request containing WMS query parameters
     * @return the tile image with a 30-day {@code Cache-Control} header;
     *         {@code 503 Service Unavailable} if GeoServer is unreachable
     * @throws IOException          if reading from the cache fails
     * @throws InterruptedException if the upstream HTTP call is interrupted
     */
    @GetMapping("/wms")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException, InterruptedException {
        // Normalise all query parameters: lowercase keys, first value wins, sorted
        TreeMap<String, String> params = new TreeMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k.toLowerCase(), v[0]));

        String cacheKey   = sha256(params.toString());
        Path   cachedFile = cacheDir.resolve(cacheKey + ".png");

        if (Files.exists(cachedFile)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).immutable())
                    .body(Files.readAllBytes(cachedFile));
        }

        // Circuit breaker: skip the upstream call if GeoServer is known to be down
        if (isCircuitOpen()) {
            log.debug("Circuit open — skipping GeoServer request for key {}", cacheKey);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        // Limit concurrent upstream calls; excess requests return 503 immediately
        // rather than queueing behind a slow/unreachable GeoServer.
        if (!upstreamPermit.tryAcquire()) {
            log.debug("All GeoServer permits in-flight — skipping for key {}", cacheKey);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        StringBuilder url = new StringBuilder(wmsUpstreamUrl).append('?');
        params.forEach((k, v) ->
                url.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                   .append('=')
                   .append(URLEncoder.encode(v, StandardCharsets.UTF_8))
                   .append('&'));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<byte[]> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            recordFailure(cacheKey, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } finally {
            upstreamPermit.release();
        }

        if (resp.statusCode() != 200) {
            log.warn("GeoServer returned HTTP {} for params {}", resp.statusCode(), params);
            return ResponseEntity.status(resp.statusCode()).build();
        }

        recordSuccess();
        byte[] body = resp.body();

        // Only serve and cache genuine image responses.  If GeoServer returns a
        // WMS ServiceException the response is XML (Content-Type: text/xml or
        // application/vnd.ogc.se_xml) even though the HTTP status is 200.
        // Forwarding that XML with Content-Type: image/png would poison the
        // browser's tile cache for up to 30 days, so return 502 instead.
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        if (!contentType.startsWith("image/")) {
            log.warn("GeoServer returned non-image content-type '{}' for key {} — returning 502",
                    contentType, cacheKey);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        Files.write(cachedFile, body);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).immutable())
                .body(body);
    }

    private boolean isCircuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntil.get();
    }

    private void recordFailure(String cacheKey, String reason) {
        int count = consecutiveFailures.incrementAndGet();
        if (count < CIRCUIT_FAILURE_THRESHOLD) {
            log.warn("GeoServer unreachable ({}/{}), no cached tile for key {}: {}",
                    count, CIRCUIT_FAILURE_THRESHOLD, cacheKey, reason);
        } else {
            circuitOpenUntil.set(System.currentTimeMillis() + CIRCUIT_OPEN_DURATION_MS);
            if (count == CIRCUIT_FAILURE_THRESHOLD) {
                log.warn("GeoServer unreachable — circuit open for {}s after {} consecutive failures; last error: {}",
                        CIRCUIT_OPEN_DURATION_MS / 1000, CIRCUIT_FAILURE_THRESHOLD, reason);
            } else {
                log.debug("GeoServer still unreachable on probe (failure #{}) — circuit re-opened for {}s: {}",
                        count, CIRCUIT_OPEN_DURATION_MS / 1000, reason);
            }
        }
    }

    private void recordSuccess() {
        if (consecutiveFailures.getAndSet(0) >= CIRCUIT_FAILURE_THRESHOLD) {
            circuitOpenUntil.set(0);
            log.info("GeoServer reachable again — circuit closed");
        }
    }

    /**
     * Computes the hex-encoded SHA-256 digest of a string.
     *
     * @param input the string to hash
     * @return lowercase hex string (64 characters)
     */
    private static String sha256(String input) {
        try {
            MessageDigest md   = MessageDigest.getInstance("SHA-256");
            byte[]        hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex  = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
