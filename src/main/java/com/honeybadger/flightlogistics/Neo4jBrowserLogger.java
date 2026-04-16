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
package com.honeybadger.flightlogistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs Neo4j connection details to the console once the application is ready.
 *
 * <p>This bean listens for the {@link ApplicationReadyEvent} and emits the
 * Neo4j Browser URL and Bolt connection string at {@code INFO} level so that
 * developers can immediately locate the database UI and connection details
 * after start-up.
 */
@Component
class Neo4jBrowserLogger {

    private static final Logger log = LoggerFactory.getLogger(Neo4jBrowserLogger.class);

    private final Neo4jBrowserInfo neo4jBrowserInfo;

    /**
     * Creates the logger with the required Neo4j connection info.
     *
     * @param neo4jBrowserInfo resolved Neo4j connection details
     */
    Neo4jBrowserLogger(Neo4jBrowserInfo neo4jBrowserInfo) {
        this.neo4jBrowserInfo = neo4jBrowserInfo;
    }

    /**
     * Emits Neo4j Browser and connection URLs to the application log.
     *
     * <p>Triggered once Spring Boot finishes starting up ({@link ApplicationReadyEvent}).
     */
    @EventListener(ApplicationReadyEvent.class)
    void logBrowserUrl() {
        log.info("Neo4j Browser: {}", neo4jBrowserInfo.browserUrl());
        log.info(
                "Neo4j connection: {} (username='{}', password='{}')",
                neo4jBrowserInfo.connectionUrl(),
                neo4jBrowserInfo.username(),
                neo4jBrowserInfo.password());
    }
}
