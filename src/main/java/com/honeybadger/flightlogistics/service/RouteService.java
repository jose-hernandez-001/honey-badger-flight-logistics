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
package com.honeybadger.flightlogistics.service;

import com.honeybadger.flightlogistics.domain.RouteEntity;
import com.honeybadger.flightlogistics.model.Route;
import com.honeybadger.flightlogistics.model.RoutePage;
import com.honeybadger.flightlogistics.model.RoutePatch;
import com.honeybadger.flightlogistics.model.RouteRequest;
import com.honeybadger.flightlogistics.model.RouteStatus;
import com.honeybadger.flightlogistics.model.PageMetadata;
import com.honeybadger.flightlogistics.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application-layer service responsible for the lifecycle of delivery routes.
 *
 * <p>All business logic for creating, reading, updating, deleting, and listing
 * routes lives here. The service translates between the API model objects
 * (generated from the OpenAPI spec) and the Neo4j domain entities.
 */
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;

    /**
     * Creates and persists a new delivery route.
     *
     * <p>If no status is supplied in the request the route defaults to
     * {@code PLANNED}. Timestamps are set to the current instant.
     *
     * @param request the route fields submitted by the caller
     * @return the newly created route API model
     */
    public Route createRoute(RouteRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        RouteEntity entity = RouteEntity.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .description(request.getDescription())
                .aircraftId(request.getAircraftId())
                .status(request.getStatus() != null
                        ? com.honeybadger.flightlogistics.domain.RouteStatus.valueOf(request.getStatus().name())
                        : com.honeybadger.flightlogistics.domain.RouteStatus.PLANNED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return toModel(routeRepository.save(entity));
    }

    /**
     * Retrieves a single route by its unique identifier.
     *
     * @param id UUID of the route
     * @return an {@link Optional} containing the route, or empty if not found
     */
    public Optional<Route> getRoute(UUID id) {
        return routeRepository.findById(id).map(this::toModel);
    }

    /**
     * Fully replaces the mutable fields of an existing route.
     *
     * @param id      UUID of the route to update
     * @param request new field values
     * @return an {@link Optional} containing the updated route, or empty if not found
     */
    public Optional<Route> updateRoute(UUID id, RouteRequest request) {
        return routeRepository.findById(id).map(entity -> {
            entity.setName(request.getName());
            entity.setDescription(request.getDescription());
            entity.setAircraftId(request.getAircraftId());
            if (request.getStatus() != null) {
                entity.setStatus(com.honeybadger.flightlogistics.domain.RouteStatus.valueOf(request.getStatus().name()));
            }
            entity.setUpdatedAt(OffsetDateTime.now());
            return toModel(routeRepository.save(entity));
        });
    }

    /**
     * Partially updates a route, applying only the non-null fields from
     * the supplied patch document.
     *
     * @param id    UUID of the route to patch
     * @param patch sparse set of fields to apply
     * @return an {@link Optional} containing the patched route, or empty if not found
     */
    public Optional<Route> patchRoute(UUID id, RoutePatch patch) {
        return routeRepository.findById(id).map(entity -> {
            if (patch.getName() != null) {
                entity.setName(patch.getName());
            }
            if (patch.getDescription() != null) {
                entity.setDescription(patch.getDescription());
            }
            if (patch.getAircraftId() != null) {
                entity.setAircraftId(patch.getAircraftId());
            }
            if (patch.getStatus() != null) {
                entity.setStatus(com.honeybadger.flightlogistics.domain.RouteStatus.valueOf(patch.getStatus().name()));
            }
            entity.setUpdatedAt(OffsetDateTime.now());
            return toModel(routeRepository.save(entity));
        });
    }

    /**
     * Deletes a route by its identifier.
     *
     * @param id UUID of the route to delete
     * @return {@code true} if the route existed and was deleted;
     *         {@code false} if no route with that ID was found
     */
    public boolean deleteRoute(UUID id) {
        if (!routeRepository.existsById(id)) {
            return false;
        }
        routeRepository.deleteById(id);
        return true;
    }

    /**
     * Returns a paginated list of routes, optionally filtered by status.
     *
     * @param status the lifecycle status to filter by, or {@code null} for all statuses
     * @param page   zero-based page index
     * @param size   maximum number of routes per page
     * @param sort   sort expression in the form {@code field,direction} (e.g.
     *               {@code "name,asc"}), or {@code null} for default ordering
     * @return a page of routes together with pagination metadata
     */
    public RoutePage listRoutes(RouteStatus status, int page, int size, String sort) {
        Pageable pageable = sort != null
                ? PageRequest.of(page, size, parseSort(sort))
                : PageRequest.of(page, size);

        Page<RouteEntity> entityPage = status != null
                ? routeRepository.findByStatus(
                        com.honeybadger.flightlogistics.domain.RouteStatus.valueOf(status.name()), pageable)
                : routeRepository.findAll(pageable);

        List<Route> routes = entityPage.getContent().stream()
                .map(this::toModel)
                .toList();

        PageMetadata metadata = new PageMetadata(
                entityPage.getNumber(),
                entityPage.getSize(),
                entityPage.getTotalElements(),
                entityPage.getTotalPages());

        return new RoutePage(routes, metadata);
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, field);
    }

    private Route toModel(RouteEntity entity) {
        Route route = new Route();
        route.setId(entity.getId());
        route.setName(entity.getName());
        route.setDescription(entity.getDescription());
        route.setAircraftId(entity.getAircraftId());
        route.setStatus(RouteStatus.valueOf(entity.getStatus().name()));
        route.setWaypointCount(entity.getWaypoints() != null ? entity.getWaypoints().size() : 0);
        route.setCreatedAt(entity.getCreatedAt());
        route.setUpdatedAt(entity.getUpdatedAt());
        return route;
    }
}
