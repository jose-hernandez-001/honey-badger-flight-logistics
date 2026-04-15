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
package com.honeybadger.flightlogistics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Provides resolved connection details for the Neo4j database instance.
 *
 * <p>Connection values are read from the application's configuration
 * ({@code spring.neo4j.*} properties) and exposed as typed accessors that
 * the UI dashboard and the startup logger can consume without re-parsing
 * the Bolt URI themselves.
 */
@Component
public class Neo4jBrowserInfo {

    private final URI boltUri;
    private final String username;
    private final String password;

    /**
     * Constructs the component by reading Neo4j connection configuration.
     *
     * @param boltUri  Bolt URI of the Neo4j server (defaults to
     *                 {@code bolt://localhost:7687})
     * @param username database username (defaults to {@code neo4j})
     * @param password database password
     */
    public Neo4jBrowserInfo(
            @Value("${spring.neo4j.uri:bolt://localhost:7687}") URI boltUri,
            @Value("${spring.neo4j.authentication.username:neo4j}") String username,
            @Value("${spring.neo4j.authentication.password:}") String password) {
        this.boltUri = boltUri;
        this.username = username;
        this.password = password;
    }

    /**
     * Returns the Neo4j Browser web application URL.
     *
     * <p>The URL scheme ({@code http} vs {@code https}) and port (7474 vs 7473)
     * are chosen automatically based on whether the Bolt URI uses a secure
     * connection scheme.
     *
     * @return the browser URL, e.g. {@code http://localhost:7474/browser/}
     */
    public String browserUrl() {
        String browserScheme = isSecureConnection() ? "https" : "http";
        int browserPort = isSecureConnection() ? 7473 : 7474;
        return String.format("%s://%s:%d/browser/", browserScheme, host(), browserPort);
    }

    /**
     * Returns the Bolt/Neo4j connection URL suitable for display in the UI.
     *
     * @return connection URL, e.g. {@code bolt://localhost:7687}
     */
    public String connectionUrl() {
        return String.format("%s://%s:%d", connectionScheme(), host(), boltPort());
    }

    /**
     * Returns the Neo4j authentication username.
     *
     * @return the configured username
     */
    public String username() {
        return username;
    }

    /**
     * Returns the Neo4j authentication password.
     *
     * @return the configured password
     */
    public String password() {
        return password;
    }

    private String connectionScheme() {
        return boltUri.getScheme() == null ? "bolt" : boltUri.getScheme();
    }

    private boolean isSecureConnection() {
        String scheme = connectionScheme();
        return scheme.contains("+s") || scheme.equals("https");
    }

    private String host() {
        return boltUri.getHost() == null ? "localhost" : boltUri.getHost();
    }

    private int boltPort() {
        return boltUri.getPort() > 0 ? boltUri.getPort() : 7687;
    }
}