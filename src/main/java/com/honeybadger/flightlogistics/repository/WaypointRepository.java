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
package com.honeybadger.flightlogistics.repository;

import com.honeybadger.flightlogistics.domain.WaypointEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data Neo4j repository for {@link WaypointEntity} nodes.
 *
 * <p>All finder methods scope their results to a specific route, ensuring
 * waypoints cannot be read or modified across route boundaries.
 */
public interface WaypointRepository extends Neo4jRepository<WaypointEntity, UUID> {

    /**
     * Returns a paginated slice of waypoints belonging to the specified route.
     *
     * @param routeId UUID of the owning route
     * @param pageable  pagination and sorting parameters
     * @return page of waypoint entities
     */
    Page<WaypointEntity> findByRouteId(UUID routeId, Pageable pageable);

    /**
     * Returns all waypoints for a route ordered by their sequence number.
     *
     * @param routeId UUID of the owning route
     * @return list of waypoints in ascending sequence order
     */
    List<WaypointEntity> findByRouteIdOrderBySequenceAsc(UUID routeId);

    /**
     * Finds a specific waypoint by its own ID and the owning route's ID.
     *
     * @param id        UUID of the waypoint
     * @param routeId UUID of the owning route
     * @return an {@link Optional} containing the waypoint, or empty if not found
     */
    Optional<WaypointEntity> findByIdAndRouteId(UUID id, UUID routeId);

    /**
     * Deletes a specific waypoint identified by both its own ID and the route ID.
     *
     * @param id        UUID of the waypoint to delete
     * @param routeId UUID of the owning route
     */
    void deleteByIdAndRouteId(UUID id, UUID routeId);

    /**
     * Returns {@code true} if a waypoint with the given sequence number already
     * exists within the specified route.
     *
     * @param routeId UUID of the owning route
     * @param sequence  the sequence number to check
     * @return {@code true} if such a waypoint exists
     */
    boolean existsByRouteIdAndSequence(UUID routeId, int sequence);

    /**
     * Counts the total number of waypoints associated with the given route.
     *
     * @param routeId UUID of the route
     * @return number of connected waypoints
     */
    @Query("MATCH (m:Route {id: $routeId})-[:HAS_WAYPOINT]->(w:Waypoint) RETURN count(w)")
    int countByRouteId(UUID routeId);
}
