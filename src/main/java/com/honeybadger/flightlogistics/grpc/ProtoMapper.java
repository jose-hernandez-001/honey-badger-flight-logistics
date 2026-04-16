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
package com.honeybadger.flightlogistics.grpc;

import com.honeybadger.flightlogistics.model.Route;
import com.honeybadger.flightlogistics.model.RoutePatch;
import com.honeybadger.flightlogistics.model.RouteRequest;
import com.honeybadger.flightlogistics.model.RouteStatus;
import com.honeybadger.flightlogistics.model.PageMetadata;
import com.honeybadger.flightlogistics.model.Waypoint;
import com.honeybadger.flightlogistics.model.WaypointPatch;
import com.honeybadger.flightlogistics.model.WaypointReorderRequest;
import com.honeybadger.flightlogistics.model.WaypointRequest;
import openapitools.RouteOuterClass;
import openapitools.RoutePatchOuterClass;
import openapitools.RouteRequestOuterClass;
import openapitools.RouteStatusOuterClass;
import openapitools.PageMetadataOuterClass;
import openapitools.WaypointListOuterClass;
import openapitools.WaypointOuterClass;
import openapitools.WaypointPageOuterClass;
import openapitools.WaypointPatchOuterClass;
import openapitools.WaypointReorderRequestOuterClass;
import openapitools.WaypointRequestOuterClass;

import java.util.List;
import java.util.UUID;

/**
 * Utility class for converting between protobuf-generated types and OpenAPI-generated model types.
 *
 * <p>All methods are package-private and stateless; instantiation is suppressed.
 */
final class ProtoMapper {

    private ProtoMapper() {}

    // ── Route ──────────────────────────────────────────────────────────────

    /**
     * Converts a protobuf {@code RouteRequest} to the OpenAPI {@link RouteRequest} model.
     */
    static RouteRequest toRouteRequest(RouteRequestOuterClass.RouteRequest proto) {
        return new RouteRequest()
                .name(proto.getName())
                .description(proto.getDescription().isEmpty() ? null : proto.getDescription())
                .aircraftId(toUuid(proto.getAircraftId()))
                .status(proto.getStatusValue() != 0 ? toRouteStatus(proto.getStatus()) : null);
    }

    /**
     * Converts a protobuf {@code RoutePatch} to the OpenAPI {@link RoutePatch} model.
     * Only fields with non-default values are populated (sparse patch semantics).
     */
    static RoutePatch toRoutePatch(RoutePatchOuterClass.RoutePatch proto) {
        RoutePatch patch = new RoutePatch();
        if (!proto.getName().isEmpty())       patch.name(proto.getName());
        if (!proto.getDescription().isEmpty()) patch.description(proto.getDescription());
        if (!proto.getAircraftId().isEmpty())  patch.aircraftId(toUuid(proto.getAircraftId()));
        if (proto.getStatusValue() != 0)       patch.status(toRouteStatus(proto.getStatus()));
        return patch;
    }

    /**
     * Converts an OpenAPI {@link Route} to its protobuf representation.
     */
    static RouteOuterClass.Route toProtoRoute(Route m) {
        RouteOuterClass.Route.Builder builder = RouteOuterClass.Route.newBuilder()
                .setId(m.getId() != null ? m.getId().toString() : "")
                .setName(m.getName() != null ? m.getName() : "")
                .setAircraftId(m.getAircraftId() != null ? m.getAircraftId().toString() : "")
                .setStatusValue(toProtoRouteStatusValue(m.getStatus()))
                .setWaypointCount(m.getWaypointCount() != null ? m.getWaypointCount() : 0);
        if (m.getDescription() != null) builder.setDescription(m.getDescription());
        return builder.build();
    }

    // ── Waypoint ─────────────────────────────────────────────────────────────

    /**
     * Converts a protobuf {@code WaypointRequest} to the OpenAPI {@link WaypointRequest} model.
     */
    static WaypointRequest toWaypointRequest(WaypointRequestOuterClass.WaypointRequest proto) {
        return new WaypointRequest()
                .name(proto.getName())
                .sequence(proto.getSequence())
                .latitude(proto.getLatitude())
                .longitude(proto.getLongitude())
                .altitude(proto.getAltitude())
                .speed(proto.getSpeed() != 0 ? proto.getSpeed() : null)
                .heading(proto.getHeading() != 0 ? proto.getHeading() : null)
                .holdTime(proto.getHoldTime() != 0 ? proto.getHoldTime() : null);
    }

    /**
     * Converts a protobuf {@code WaypointPatch} to the OpenAPI {@link WaypointPatch} model.
     * Only fields with non-default values are populated (sparse patch semantics).
     */
    static WaypointPatch toWaypointPatch(WaypointPatchOuterClass.WaypointPatch proto) {
        WaypointPatch patch = new WaypointPatch();
        if (!proto.getName().isEmpty()) patch.name(proto.getName());
        if (proto.getSequence() != 0)   patch.sequence(proto.getSequence());
        if (proto.getLatitude() != 0)   patch.latitude(proto.getLatitude());
        if (proto.getLongitude() != 0)  patch.longitude(proto.getLongitude());
        if (proto.getAltitude() != 0)   patch.altitude(proto.getAltitude());
        if (proto.getSpeed() != 0)      patch.speed(proto.getSpeed());
        if (proto.getHeading() != 0)    patch.heading(proto.getHeading());
        if (proto.getHoldTime() != 0)   patch.holdTime(proto.getHoldTime());
        return patch;
    }

    /**
     * Converts an OpenAPI {@link Waypoint} to its protobuf representation.
     */
    static WaypointOuterClass.Waypoint toProtoWaypoint(Waypoint w) {
        return WaypointOuterClass.Waypoint.newBuilder()
                .setId(w.getId() != null ? w.getId().toString() : "")
                .setRouteId(w.getRouteId() != null ? w.getRouteId().toString() : "")
                .setName(w.getName() != null ? w.getName() : "")
                .setSequence(w.getSequence() != null ? w.getSequence() : 0)
                .setLatitude(w.getLatitude() != null ? w.getLatitude() : 0.0)
                .setLongitude(w.getLongitude() != null ? w.getLongitude() : 0.0)
                .setAltitude(w.getAltitude() != null ? w.getAltitude() : 0.0)
                .setSpeed(w.getSpeed() != null ? w.getSpeed() : 0.0)
                .setHeading(w.getHeading() != null ? w.getHeading() : 0.0)
                .setHoldTime(w.getHoldTime() != null ? w.getHoldTime() : 0)
                .build();
    }

    /**
     * Converts a protobuf {@code WaypointReorderRequest} to the OpenAPI {@link WaypointReorderRequest}.
     */
    static WaypointReorderRequest toWaypointReorderRequest(
            WaypointReorderRequestOuterClass.WaypointReorderRequest proto) {
        WaypointReorderRequest req = new WaypointReorderRequest();
        proto.getWaypointIdsList().stream()
                .map(UUID::fromString)
                .forEach(req::addWaypointIdsItem);
        return req;
    }

    // ── Page / List ───────────────────────────────────────────────────────────

    /**
     * Converts an OpenAPI {@link PageMetadata} to its protobuf representation.
     */
    static PageMetadataOuterClass.PageMetadata toProtoPageMetadata(PageMetadata meta) {
        if (meta == null) return PageMetadataOuterClass.PageMetadata.getDefaultInstance();
        return PageMetadataOuterClass.PageMetadata.newBuilder()
                .setNumber(meta.getNumber() != null ? meta.getNumber() : 0)
                .setSize(meta.getSize() != null ? meta.getSize() : 0)
                .setTotalElements(meta.getTotalElements() != null ? meta.getTotalElements() : 0L)
                .setTotalPages(meta.getTotalPages() != null ? meta.getTotalPages() : 0)
                .build();
    }

    /**
     * Builds a protobuf {@code WaypointPage} from a list of OpenAPI {@link Waypoint}s and metadata.
     */
    static WaypointPageOuterClass.WaypointPage toProtoWaypointPage(
            List<Waypoint> content, PageMetadata meta) {
        WaypointPageOuterClass.WaypointPage.Builder builder =
                WaypointPageOuterClass.WaypointPage.newBuilder();
        if (content != null) {
            content.stream().map(ProtoMapper::toProtoWaypoint).forEach(builder::addContent);
        }
        builder.setPage(toProtoPageMetadata(meta));
        return builder.build();
    }

    /**
     * Builds a protobuf {@code WaypointList} from a list of OpenAPI {@link Waypoint}s.
     */
    static WaypointListOuterClass.WaypointList toProtoWaypointList(List<Waypoint> waypoints) {
        WaypointListOuterClass.WaypointList.Builder builder =
                WaypointListOuterClass.WaypointList.newBuilder();
        if (waypoints != null) {
            waypoints.stream().map(ProtoMapper::toProtoWaypoint).forEach(builder::addWaypoints);
        }
        return builder.build();
    }

    // ── Enum ─────────────────────────────────────────────────────────────────

    /**
     * Converts a protobuf {@code RouteStatus} enum value to the OpenAPI {@link RouteStatus}.
     */
    static RouteStatus toRouteStatus(RouteStatusOuterClass.RouteStatus proto) {
        return switch (proto) {
            case ROUTE_STATUS_PLANNED   -> RouteStatus.PLANNED;
            case ROUTE_STATUS_ACTIVE    -> RouteStatus.ACTIVE;
            case ROUTE_STATUS_PAUSED    -> RouteStatus.PAUSED;
            case ROUTE_STATUS_COMPLETED -> RouteStatus.COMPLETED;
            case ROUTE_STATUS_ABORTED   -> RouteStatus.ABORTED;
            default                       -> null;
        };
    }

    /**
     * Converts an OpenAPI {@link RouteStatus} to the corresponding protobuf numeric value.
     */
    static int toProtoRouteStatusValue(RouteStatus status) {
        if (status == null) return 0;
        return switch (status) {
            case PLANNED   -> RouteStatusOuterClass.RouteStatus.ROUTE_STATUS_PLANNED_VALUE;
            case ACTIVE    -> RouteStatusOuterClass.RouteStatus.ROUTE_STATUS_ACTIVE_VALUE;
            case PAUSED    -> RouteStatusOuterClass.RouteStatus.ROUTE_STATUS_PAUSED_VALUE;
            case COMPLETED -> RouteStatusOuterClass.RouteStatus.ROUTE_STATUS_COMPLETED_VALUE;
            case ABORTED   -> RouteStatusOuterClass.RouteStatus.ROUTE_STATUS_ABORTED_VALUE;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parses a string into a {@link UUID}, returning {@code null} for null or empty input.
     */
    static UUID toUuid(String s) {
        return (s == null || s.isEmpty()) ? null : UUID.fromString(s);
    }
}
