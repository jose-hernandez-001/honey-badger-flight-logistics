/*
 * MIT License
 *
 * Copyright (c) 2026 José Hernández
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.honeybadger.flightlogistics.controller;

import com.honeybadger.flightlogistics.Neo4jBrowserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Web MVC controller that serves the application's home page and exposes
 * lightweight info APIs consumed by the frontend.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@code GET /} — redirects to {@code /index.html}</li>
 *   <li>{@code GET /api/neo4j/browser-url} — returns Neo4j browser connection details</li>
 *   <li>{@code GET /api/geoserver/info} — returns the GeoServer web-admin URL</li>
 * </ul>
 */
@Controller
public class HomeController {

    private final Neo4jBrowserInfo neo4jBrowserInfo;
    private final String geoServerWmsUrl;

    /**
     * Creates a new {@code HomeController}.
     *
     * @param neo4jBrowserInfo  Neo4j browser connection details resolved at startup
     * @param geoServerWmsUrl   WMS endpoint URL injected from {@code geoserver.wms-url}
     */
    public HomeController(
            Neo4jBrowserInfo neo4jBrowserInfo,
            @Value("${geoserver.wms-url:http://localhost:8600/geoserver/wms}") String geoServerWmsUrl) {
        this.neo4jBrowserInfo = neo4jBrowserInfo;
        this.geoServerWmsUrl  = geoServerWmsUrl;
    }

    /**
     * Redirects the root path to the main dashboard page.
     *
     * @return Spring MVC view name for a redirect to {@code /index.html}
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/index.html";
    }

    /**
     * Returns Neo4j browser connection details as a JSON map.
     *
     * <p>The response includes {@code browserUrl}, {@code connectionUrl},
     * {@code username}, and {@code password} keys.
     *
     * @return map of Neo4j connection properties
     */
    @GetMapping("/api/neo4j/browser-url")
    @ResponseBody
    public Map<String, String> neo4jBrowserUrl() {
        return Map.of(
                "browserUrl", neo4jBrowserInfo.browserUrl(),
                "connectionUrl", neo4jBrowserInfo.connectionUrl(),
                "username", neo4jBrowserInfo.username(),
                "password", neo4jBrowserInfo.password());
    }

    /**
     * Returns GeoServer administration details as a JSON map.
     *
     * <p>Derives the web-admin URL from the configured WMS URL by replacing
     * the trailing {@code /wms} segment with {@code /web/}.
     *
     * @return map containing {@code adminUrl} for the GeoServer web console
     */
    @GetMapping("/api/geoserver/info")
    @ResponseBody
    public Map<String, String> geoServerInfo() {
        // Derive the web admin URL from the configured WMS URL
        String adminUrl = geoServerWmsUrl.replaceAll("/wms$", "/web/");
        return Map.of("adminUrl", adminUrl);
    }
}
