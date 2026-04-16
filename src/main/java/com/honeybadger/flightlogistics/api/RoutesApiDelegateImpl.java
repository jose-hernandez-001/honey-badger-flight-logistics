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

import com.honeybadger.flightlogistics.model.Route;
import com.honeybadger.flightlogistics.model.RoutePage;
import com.honeybadger.flightlogistics.model.RoutePatch;
import com.honeybadger.flightlogistics.model.RouteRequest;
import com.honeybadger.flightlogistics.model.RouteStatus;
import com.honeybadger.flightlogistics.service.RouteService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Delegate implementation for the OpenAPI-generated {@code RoutesApi}.
 *
 * <p>Bridges the generated Spring MVC controller interface to the
 * {@link RouteService} and maps service results to appropriate HTTP responses.
 * The delegate pattern keeps the generated controller code untouched and confines
 * hand-written logic to this class.
 */
@Service
@RequiredArgsConstructor
public class RoutesApiDelegateImpl implements RoutesApiDelegate {

    private final RouteService routeService;

    /**
     * Handles {@code POST /api/v1/routes} — creates a new delivery route.
     *
     * @param routeRequest the route fields from the request body
     * @return {@code 201 Created} with the new route in the body
     */
    @Override
    public ResponseEntity<Route> createRoute(RouteRequest routeRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(routeService.createRoute(routeRequest));
    }

    /**
     * Handles {@code DELETE /api/v1/routes/{routeId}} — deletes a route.
     *
     * @param routeId UUID path variable identifying the route
     * @return {@code 204 No Content} on success; {@code 404 Not Found} if the route does not exist
     */
    @Override
    public ResponseEntity<Void> deleteRoute(UUID routeId) {
        return routeService.deleteRoute(routeId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Handles {@code GET /api/v1/routes/{routeId}} — retrieves a single route.
     *
     * @param routeId UUID path variable identifying the route
     * @return {@code 200 OK} with the route body; {@code 404 Not Found} if absent
     */
    @Override
    public ResponseEntity<Route> getRoute(UUID routeId) {
        return routeService.getRoute(routeId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Handles {@code GET /api/v1/routes} — lists routes with optional filtering.
     *
     * @param status filter by lifecycle status (optional)
     * @param page   zero-based page index (defaults to 0)
     * @param size   page size (defaults to 20)
     * @param sort   sort expression, e.g. {@code "name,asc"} (optional)
     * @return {@code 200 OK} with a paginated route list
     */
    @Override
    public ResponseEntity<RoutePage> listRoutes(RouteStatus status, Integer page, Integer size, String sort) {
        return ResponseEntity.ok(routeService.listRoutes(
                status,
                page != null ? page : 0,
                size != null ? size : 20,
                sort));
    }

    /**
     * Handles {@code PATCH /api/v1/routes/{routeId}} — partially updates a route.
     *
     * @param routeId   UUID of the route to patch
     * @param routePatch sparse patch document
     * @return {@code 200 OK} with the updated route; {@code 404 Not Found} if absent
     */
    @Override
    public ResponseEntity<Route> patchRoute(UUID routeId, RoutePatch routePatch) {
        return routeService.patchRoute(routeId, routePatch)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Handles {@code PUT /api/v1/routes/{routeId}} — fully replaces a route.
     *
     * @param routeId      UUID of the route to update
     * @param routeRequest replacement fields
     * @return {@code 200 OK} with the updated route; {@code 404 Not Found} if absent
     */
    @Override
    public ResponseEntity<Route> updateRoute(UUID routeId, RouteRequest routeRequest) {
        return routeService.updateRoute(routeId, routeRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
