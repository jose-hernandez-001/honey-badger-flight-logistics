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
package com.honeybadger.flightlogistics.api;

import com.honeybadger.flightlogistics.model.Waypoint;
import com.honeybadger.flightlogistics.model.WaypointList;
import com.honeybadger.flightlogistics.model.WaypointPage;
import com.honeybadger.flightlogistics.model.WaypointPatch;
import com.honeybadger.flightlogistics.model.WaypointReorderRequest;
import com.honeybadger.flightlogistics.model.WaypointRequest;
import com.honeybadger.flightlogistics.service.WaypointService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Delegate implementation for the OpenAPI-generated {@code WaypointsApi}.
 *
 * <p>Bridges the generated Spring MVC controller interface to the
 * {@link WaypointService} and maps service results to appropriate HTTP
 * responses. All operations are scoped to a specific route identified by
 * the {@code routeId} path variable.
 */
@Service
@RequiredArgsConstructor
public class WaypointsApiDelegateImpl implements WaypointsApiDelegate {

    private final WaypointService waypointService;

    /**
     * Handles {@code POST /api/v1/routes/{routeId}/waypoints} — adds a waypoint.
     *
     * @param routeId      UUID of the parent route
     * @param waypointRequest the waypoint fields from the request body
     * @return {@code 201 Created} with the new waypoint; {@code 404 Not Found} if the route is absent
     */
    @Override
    public ResponseEntity<Waypoint> createWaypoint(UUID routeId, WaypointRequest waypointRequest) {
        return waypointService.createWaypoint(routeId, waypointRequest)
                .map(w -> ResponseEntity.status(HttpStatus.CREATED).body(w))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Handles {@code DELETE /api/v1/routes/{routeId}/waypoints/{waypointId}}.
     *
     * @param routeId  UUID of the parent route
     * @param waypointId UUID of the waypoint to delete
     * @return {@code 204 No Content} on success; {@code 404 Not Found} if not found
     */
    @Override
    public ResponseEntity<Void> deleteWaypoint(UUID routeId, UUID waypointId) {
        return waypointService.deleteWaypoint(routeId, waypointId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Handles {@code GET /api/v1/routes/{routeId}/waypoints/{waypointId}}.
     *
     * @param routeId  UUID of the parent route
     * @param waypointId UUID of the waypoint to retrieve
     * @return {@code 200 OK} with the waypoint body; {@code 404 Not Found} if absent
     */
    @Override
    public ResponseEntity<Waypoint> getWaypoint(UUID routeId, UUID waypointId) {
        return waypointService.getWaypoint(routeId, waypointId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Handles {@code GET /api/v1/routes/{routeId}/waypoints} — lists waypoints.
     *
     * @param routeId UUID of the parent route
     * @param page      zero-based page index (defaults to 0)
     * @param size      page size (defaults to 20)
     * @param sort      sort expression (optional)
     * @return {@code 200 OK} with paginated waypoints; {@code 404} if the route is absent
     */
    @Override
    public ResponseEntity<WaypointPage> listWaypoints(UUID routeId, Integer page, Integer size, String sort) {
        return waypointService.listWaypoints(
                        routeId,
                        page != null ? page : 0,
                        size != null ? size : 20,
                        sort)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Handles {@code PATCH /api/v1/routes/{routeId}/waypoints/{waypointId}}.
     *
     * @param routeId    UUID of the parent route
     * @param waypointId   UUID of the waypoint to patch
     * @param waypointPatch sparse patch document
     * @return {@code 200 OK} with the updated waypoint; {@code 404 Not Found} if absent
     */
    @Override
    public ResponseEntity<Waypoint> patchWaypoint(UUID routeId, UUID waypointId, WaypointPatch waypointPatch) {
        return waypointService.patchWaypoint(routeId, waypointId, waypointPatch)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Handles {@code PUT /api/v1/routes/{routeId}/waypoints/reorder} —
     * reassigns sequence numbers according to the supplied ordered list.
     *
     * @param routeId              UUID of the parent route
     * @param waypointReorderRequest ordered list of waypoint UUIDs
     * @return {@code 200 OK} with the reordered waypoints; {@code 404 Not Found} on failure
     */
    @Override
    public ResponseEntity<WaypointList> reorderWaypoints(UUID routeId, WaypointReorderRequest waypointReorderRequest) {
        return waypointService.reorderWaypoints(routeId, waypointReorderRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Handles {@code PUT /api/v1/routes/{routeId}/waypoints/{waypointId}} —
     * fully replaces a waypoint.
     *
     * @param routeId      UUID of the parent route
     * @param waypointId     UUID of the waypoint to update
     * @param waypointRequest replacement fields
     * @return {@code 200 OK} with the updated waypoint; {@code 404 Not Found} if absent
     */
    @Override
    public ResponseEntity<Waypoint> updateWaypoint(UUID routeId, UUID waypointId, WaypointRequest waypointRequest) {
        return waypointService.updateWaypoint(routeId, waypointId, waypointRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
