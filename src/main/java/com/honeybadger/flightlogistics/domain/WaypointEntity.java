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
package com.honeybadger.flightlogistics.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.annotation.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Neo4j graph node representing a single stop (waypoint) on a delivery route.
 *
 * <p>Waypoints are ordered by their {@link #sequence} number and together
 * form the flight path of a {@link RouteEntity}. Geographic position is
 * expressed in WGS 84 coordinates (latitude, longitude, altitude in metres).
 */
@Node("Waypoint")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaypointEntity {

    /** Unique identifier for this waypoint. */
    @Id
    private UUID id;

    /** Optimistic-locking version counter managed by Spring Data. */
    @Version
    private Long version;

    /** Identifier of the parent route that owns this waypoint. */
    private UUID routeId;

    /** Human-readable label for this stop (e.g. "Pickup – Warehouse A"). */
    private String name;

    /** 1-based position of this waypoint in the route route. */
    private int sequence;

    /** WGS 84 latitude in decimal degrees. */
    private double latitude;

    /** WGS 84 longitude in decimal degrees. */
    private double longitude;

    /** Altitude above mean sea level in metres. */
    private double altitude;

    /** Target airspeed at this waypoint in metres per second (optional). */
    private Double speed;

    /** Compass heading at this waypoint in degrees [0, 360) (optional). */
    private Double heading;

    /**
     * Time the aircraft should loiter at this waypoint, in seconds.
     * Defaults to {@code 0} (no hold).
     */
    @Builder.Default
    private int holdTime = 0;

    /** Timestamp when this waypoint record was first created. */
    private OffsetDateTime createdAt;

    /** Timestamp of the most recent update to this waypoint record. */
    private OffsetDateTime updatedAt;
}
