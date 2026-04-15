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
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.schema.Relationship.Direction;
import org.springframework.data.annotation.Version;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Neo4j graph node representing a delivery route.
 *
 * <p>A route is the top-level planning unit in the system. It groups a
 * sequence of {@link WaypointEntity waypoints} that define the route and
 * stop schedule for a single aircraft delivery run.
 *
 * <p>Persisted as a {@code Route} node in the graph. Waypoints are linked
 * via outgoing {@code HAS_WAYPOINT} relationships.
 */
@Node("Route")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteEntity {

    /** Unique identifier for this route (UUID stored as a string property). */
    @Id
    private UUID id;

    /** Optimistic-locking version counter managed by Spring Data. */
    @Version
    private Long version;

    /** Human-readable name of the route (e.g. "Route 47 – Morning Run"). */
    private String name;

    /** Optional free-text description of the route's purpose or special notes. */
    private String description;

    /** Identifier of the aircraft (drone) assigned to this route. */
    private UUID aircraftId;

    /** Current lifecycle status of the route. */
    private RouteStatus status;

    /** Timestamp when this route record was first created. */
    private OffsetDateTime createdAt;

    /** Timestamp of the most recent update to this route record. */
    private OffsetDateTime updatedAt;

    /**
     * Ordered list of waypoints associated with this route.
     *
     * <p>Each waypoint is connected via an outgoing {@code HAS_WAYPOINT}
     * relationship in the Neo4j graph.
     */
    @Relationship(type = "HAS_WAYPOINT", direction = Direction.OUTGOING)
    @Builder.Default
    private List<WaypointEntity> waypoints = new ArrayList<>();
}
