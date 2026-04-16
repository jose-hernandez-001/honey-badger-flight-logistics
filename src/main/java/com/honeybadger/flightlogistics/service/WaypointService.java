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
package com.honeybadger.flightlogistics.service;

import com.honeybadger.flightlogistics.domain.WaypointEntity;
import com.honeybadger.flightlogistics.model.Waypoint;
import com.honeybadger.flightlogistics.model.WaypointList;
import com.honeybadger.flightlogistics.model.WaypointPage;
import com.honeybadger.flightlogistics.model.WaypointPatch;
import com.honeybadger.flightlogistics.model.WaypointRequest;
import com.honeybadger.flightlogistics.model.WaypointReorderRequest;
import com.honeybadger.flightlogistics.model.PageMetadata;
import com.honeybadger.flightlogistics.repository.RouteRepository;
import com.honeybadger.flightlogistics.repository.WaypointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application-layer service for managing waypoints within a delivery route.
 *
 * <p>Waypoints define the ordered stops on a route's route. Every mutating
 * operation verifies that the owning route exists before making changes, and
 * all queries are scoped to a single route to prevent cross-route data
 * leakage.
 */
@Service
@RequiredArgsConstructor
public class WaypointService {

    private final WaypointRepository waypointRepository;
    private final RouteRepository routeRepository;

    /**
     * Creates a new waypoint and links it to the specified route.
     *
     * <p>Returns {@link Optional#empty()} if the route does not exist.
     *
     * @param routeId UUID of the owning route
     * @param request   waypoint fields submitted by the caller
     * @return an {@link Optional} containing the new waypoint, or empty if the route was not found
     */
    public Optional<Waypoint> createWaypoint(UUID routeId, WaypointRequest request) {
        return routeRepository.findById(routeId).map(route -> {
            OffsetDateTime now = OffsetDateTime.now();
            WaypointEntity entity = WaypointEntity.builder()
                    .id(UUID.randomUUID())
                    .routeId(routeId)
                    .name(request.getName())
                    .sequence(request.getSequence())
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .altitude(request.getAltitude())
                    .speed(request.getSpeed())
                    .heading(request.getHeading())
                    .holdTime(request.getHoldTime() != null ? request.getHoldTime() : 0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            WaypointEntity saved = waypointRepository.save(entity);
            route.getWaypoints().add(saved);
            routeRepository.save(route);
            return toModel(saved);
        });
    }

    /**
     * Deletes a waypoint from a route.
     *
     * @param routeId  UUID of the owning route
     * @param waypointId UUID of the waypoint to delete
     * @return {@code true} if the waypoint was found and deleted;
     *         {@code false} if no matching waypoint exists
     */
    public boolean deleteWaypoint(UUID routeId, UUID waypointId) {
        if (waypointRepository.findByIdAndRouteId(waypointId, routeId).isEmpty()) {
            return false;
        }
        waypointRepository.deleteByIdAndRouteId(waypointId, routeId);
        routeRepository.findById(routeId).ifPresent(route -> {
            route.getWaypoints().removeIf(w -> waypointId.equals(w.getId()));
            routeRepository.save(route);
        });
        return true;
    }

    /**
     * Retrieves a single waypoint by its own ID within a given route.
     *
     * @param routeId  UUID of the owning route
     * @param waypointId UUID of the waypoint
     * @return an {@link Optional} containing the waypoint, or empty if not found
     */
    public Optional<Waypoint> getWaypoint(UUID routeId, UUID waypointId) {
        return waypointRepository.findByIdAndRouteId(waypointId, routeId).map(this::toModel);
    }

    /**
     * Returns a paginated list of waypoints for the specified route.
     *
     * <p>Returns {@link Optional#empty()} if the parent route does not exist.
     *
     * @param routeId UUID of the owning route
     * @param page      zero-based page index
     * @param size      maximum number of waypoints per page
     * @param sort      sort expression (e.g. {@code "sequence,asc"}), or {@code null}
     * @return an {@link Optional} containing the waypoint page
     */
    public Optional<WaypointPage> listWaypoints(UUID routeId, int page, int size, String sort) {
        if (!routeRepository.existsById(routeId)) {
            return Optional.empty();
        }
        Pageable pageable = sort != null
                ? PageRequest.of(page, size, parseSort(sort))
                : PageRequest.of(page, size);
        Page<WaypointEntity> entityPage = waypointRepository.findByRouteId(routeId, pageable);
        List<Waypoint> waypoints = entityPage.getContent().stream().map(this::toModel).toList();
        PageMetadata metadata = new PageMetadata(
                entityPage.getNumber(),
                entityPage.getSize(),
                entityPage.getTotalElements(),
                entityPage.getTotalPages());
        return Optional.of(new WaypointPage(waypoints, metadata));
    }

    /**
     * Fully replaces all mutable fields of an existing waypoint.
     *
     * @param routeId  UUID of the owning route
     * @param waypointId UUID of the waypoint to update
     * @param request    new field values
     * @return an {@link Optional} containing the updated waypoint, or empty if not found
     */
    public Optional<Waypoint> updateWaypoint(UUID routeId, UUID waypointId, WaypointRequest request) {
        return waypointRepository.findByIdAndRouteId(waypointId, routeId).map(entity -> {
            entity.setName(request.getName());
            entity.setSequence(request.getSequence());
            entity.setLatitude(request.getLatitude());
            entity.setLongitude(request.getLongitude());
            entity.setAltitude(request.getAltitude());
            entity.setSpeed(request.getSpeed());
            entity.setHeading(request.getHeading());
            entity.setHoldTime(request.getHoldTime() != null ? request.getHoldTime() : 0);
            entity.setUpdatedAt(OffsetDateTime.now());
            return toModel(waypointRepository.save(entity));
        });
    }

    /**
     * Partially updates a waypoint, applying only non-null fields from the patch.
     *
     * @param routeId  UUID of the owning route
     * @param waypointId UUID of the waypoint to patch
     * @param patch      sparse set of fields to apply
     * @return an {@link Optional} containing the patched waypoint, or empty if not found
     */
    public Optional<Waypoint> patchWaypoint(UUID routeId, UUID waypointId, WaypointPatch patch) {
        return waypointRepository.findByIdAndRouteId(waypointId, routeId).map(entity -> {
            if (patch.getName() != null) entity.setName(patch.getName());
            if (patch.getSequence() != null) entity.setSequence(patch.getSequence());
            if (patch.getLatitude() != null) entity.setLatitude(patch.getLatitude());
            if (patch.getLongitude() != null) entity.setLongitude(patch.getLongitude());
            if (patch.getAltitude() != null) entity.setAltitude(patch.getAltitude());
            if (patch.getSpeed() != null) entity.setSpeed(patch.getSpeed());
            if (patch.getHeading() != null) entity.setHeading(patch.getHeading());
            if (patch.getHoldTime() != null) entity.setHoldTime(patch.getHoldTime());
            entity.setUpdatedAt(OffsetDateTime.now());
            return toModel(waypointRepository.save(entity));
        });
    }

    /**
     * Re-sequences the waypoints of a route according to the supplied ordered
     * list of waypoint IDs.
     *
     * <p>Each waypoint's {@code sequence} field is reassigned to its 1-based
     * position in the provided list. Returns {@link Optional#empty()} if the
     * route does not exist or if any of the supplied IDs do not belong to it.
     *
     * @param routeId UUID of the owning route
     * @param request   the desired ordering expressed as an ordered list of waypoint UUIDs
     * @return an {@link Optional} containing the reordered waypoints, or empty on failure
     */
    public Optional<WaypointList> reorderWaypoints(UUID routeId, WaypointReorderRequest request) {
        if (!routeRepository.existsById(routeId)) {
            return Optional.empty();
        }
        List<UUID> orderedIds = request.getWaypointIds();
        List<WaypointEntity> allWaypoints = waypointRepository.findByRouteIdOrderBySequenceAsc(routeId);

        Set<UUID> routeWaypointIds = allWaypoints.stream()
                .map(WaypointEntity::getId)
                .collect(Collectors.toSet());
        if (!routeWaypointIds.containsAll(orderedIds)) {
            return Optional.empty();
        }

        Map<UUID, WaypointEntity> waypointMap = allWaypoints.stream()
                .collect(Collectors.toMap(WaypointEntity::getId, w -> w));

        OffsetDateTime now = OffsetDateTime.now();
        List<WaypointEntity> toSave = new ArrayList<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            WaypointEntity entity = waypointMap.get(orderedIds.get(i));
            entity.setSequence(i + 1);
            entity.setUpdatedAt(now);
            toSave.add(entity);
        }

        List<Waypoint> result = waypointRepository.saveAll(toSave).stream()
                .sorted(Comparator.comparingInt(WaypointEntity::getSequence))
                .map(this::toModel)
                .toList();

        return Optional.of(new WaypointList(result));
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, field);
    }

    private Waypoint toModel(WaypointEntity entity) {
        Waypoint waypoint = new Waypoint();
        waypoint.setId(entity.getId());
        waypoint.setRouteId(entity.getRouteId());
        waypoint.setName(entity.getName());
        waypoint.setSequence(entity.getSequence());
        waypoint.setLatitude(entity.getLatitude());
        waypoint.setLongitude(entity.getLongitude());
        waypoint.setAltitude(entity.getAltitude());
        waypoint.setSpeed(entity.getSpeed());
        waypoint.setHeading(entity.getHeading());
        waypoint.setHoldTime(entity.getHoldTime());
        waypoint.setCreatedAt(entity.getCreatedAt());
        waypoint.setUpdatedAt(entity.getUpdatedAt());
        return waypoint;
    }
}
