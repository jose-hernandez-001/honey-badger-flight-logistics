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

import com.honeybadger.flightlogistics.domain.RouteEntity;
import com.honeybadger.flightlogistics.domain.RouteStatus;
import com.honeybadger.flightlogistics.domain.WaypointEntity;
import com.honeybadger.flightlogistics.repository.RouteRepository;
import com.honeybadger.flightlogistics.repository.WaypointRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Loads demo seed data into Neo4j on startup when {@code app.demo.seed-data=true}.
 *
 * <p>The seed data is read from {@code classpath:demo-data.json} and contains
 * the four reference routes and their waypoints used for demonstrations.
 * The loader is a no-op if any routes already exist in the database, so it
 * is safe to leave the property enabled at all times without risk of creating
 * duplicate records.
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed-data", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoDataLoader implements ApplicationRunner {

    private final RouteRepository routeRepository;
    private final WaypointRepository waypointRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (routeRepository.count() > 0) {
            log.info("Demo data already present – skipping seed.");
            return;
        }

        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SeedData seed = mapper.readValue(
                new ClassPathResource("demo-data.json").getInputStream(),
                SeedData.class);

        OffsetDateTime now = OffsetDateTime.now();

        for (RouteSeed ms : seed.routes()) {
            RouteEntity route = RouteEntity.builder()
                    .id(UUID.fromString(ms.id()))
                    .name(ms.name())
                    .description(ms.description())
                    .aircraftId(UUID.fromString(ms.aircraftId()))
                    .status(RouteStatus.valueOf(ms.status()))
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            route = routeRepository.save(route);

            for (WaypointSeed ws : ms.waypoints()) {
                WaypointEntity wp = WaypointEntity.builder()
                        .id(UUID.fromString(ws.id()))
                        .routeId(route.getId())
                        .name(ws.name())
                        .sequence(ws.sequence())
                        .latitude(ws.latitude())
                        .longitude(ws.longitude())
                        .altitude(ws.altitude())
                        .speed(ws.speed())
                        .heading(ws.heading())
                        .holdTime(ws.holdTime())
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                WaypointEntity savedWp = waypointRepository.save(wp);
                route.getWaypoints().add(savedWp);
            }

            routeRepository.save(route);
            log.info("Seeded route '{}' with {} waypoint(s).",
                    route.getName(), ms.waypoints().size());
        }

        log.info("Demo data seed complete: {} routes loaded.", seed.routes().size());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeedData(List<RouteSeed> routes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RouteSeed(
            String id,
            String name,
            String description,
            String aircraftId,
            String status,
            List<WaypointSeed> waypoints) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WaypointSeed(
            String id,
            String name,
            int sequence,
            double latitude,
            double longitude,
            double altitude,
            Double speed,
            Double heading,
            int holdTime) {}
}
