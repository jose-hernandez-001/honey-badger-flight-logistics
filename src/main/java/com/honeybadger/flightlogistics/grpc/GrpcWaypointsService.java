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

import com.honeybadger.flightlogistics.api.WaypointsApiDelegateImpl;
import com.honeybadger.flightlogistics.model.WaypointPage;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import openapitools.WaypointListOuterClass;
import openapitools.WaypointPageOuterClass;
import openapitools.services.waypointsservice.WaypointsServiceGrpc;
import openapitools.services.waypointsservice.WaypointsServiceOuterClass;
import org.springframework.grpc.server.service.GrpcService;

import java.util.UUID;

/**
 * gRPC service implementation for waypoint operations.
 *
 * <p>Each RPC is a thin adapter that converts the incoming protobuf request to the
 * OpenAPI model types expected by {@link WaypointsApiDelegateImpl}, delegates the call,
 * then maps the {@code ResponseEntity} result back to the appropriate protobuf response
 * {@code oneof} variant. No business logic lives here.
 */
@GrpcService
@RequiredArgsConstructor
public class GrpcWaypointsService extends WaypointsServiceGrpc.WaypointsServiceImplBase {

    private final WaypointsApiDelegateImpl delegate;

    @Override
    public void createWaypoint(
            WaypointsServiceOuterClass.CreateWaypointRequest request,
            StreamObserver<WaypointsServiceOuterClass.CreateWaypointResponse> responseObserver) {
        var httpResponse = delegate.createWaypoint(
                UUID.fromString(request.getRouteId()),
                ProtoMapper.toWaypointRequest(request.getWaypointRequest()));
        var builder = WaypointsServiceOuterClass.CreateWaypointResponse.newBuilder();
        if (httpResponse.getStatusCode().is2xxSuccessful() && httpResponse.getBody() != null) {
            builder.setWaypoint201(ProtoMapper.toProtoWaypoint(httpResponse.getBody()));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void deleteWaypoint(
            WaypointsServiceOuterClass.DeleteWaypointRequest request,
            StreamObserver<WaypointsServiceOuterClass.DeleteWaypointResponse> responseObserver) {
        var httpResponse = delegate.deleteWaypoint(
                UUID.fromString(request.getRouteId()),
                UUID.fromString(request.getWaypointId()));
        var builder = WaypointsServiceOuterClass.DeleteWaypointResponse.newBuilder();
        if (httpResponse.getStatusCode().is2xxSuccessful()) {
            builder.setEmpty204(Empty.getDefaultInstance());
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getWaypoint(
            WaypointsServiceOuterClass.GetWaypointRequest request,
            StreamObserver<WaypointsServiceOuterClass.GetWaypointResponse> responseObserver) {
        var httpResponse = delegate.getWaypoint(
                UUID.fromString(request.getRouteId()),
                UUID.fromString(request.getWaypointId()));
        var builder = WaypointsServiceOuterClass.GetWaypointResponse.newBuilder();
        if (httpResponse.getStatusCode().is2xxSuccessful() && httpResponse.getBody() != null) {
            builder.setWaypoint200(ProtoMapper.toProtoWaypoint(httpResponse.getBody()));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void listWaypoints(
            WaypointsServiceOuterClass.ListWaypointsRequest request,
            StreamObserver<WaypointsServiceOuterClass.ListWaypointsResponse> responseObserver) {
        var sort = request.getSort().isEmpty() ? null : request.getSort();
        var httpResponse = delegate.listWaypoints(
                UUID.fromString(request.getRouteId()),
                request.getPage(),
                request.getSize(),
                sort);
        var builder = WaypointsServiceOuterClass.ListWaypointsResponse.newBuilder();
        if (httpResponse.getStatusCode().is2xxSuccessful() && httpResponse.getBody() != null) {
            builder.setWaypointPage200(toProtoWaypointPage(httpResponse.getBody()));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void patchWaypoint(
            WaypointsServiceOuterClass.PatchWaypointRequest request,
            StreamObserver<WaypointsServiceOuterClass.PatchWaypointResponse> responseObserver) {
        var httpResponse = delegate.patchWaypoint(
                UUID.fromString(request.getRouteId()),
                UUID.fromString(request.getWaypointId()),
                ProtoMapper.toWaypointPatch(request.getWaypointPatch()));
        var builder = WaypointsServiceOuterClass.PatchWaypointResponse.newBuilder();
        if (httpResponse.getStatusCode().is2xxSuccessful() && httpResponse.getBody() != null) {
            builder.setWaypoint200(ProtoMapper.toProtoWaypoint(httpResponse.getBody()));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void reorderWaypoints(
            WaypointsServiceOuterClass.ReorderWaypointsRequest request,
            StreamObserver<WaypointsServiceOuterClass.ReorderWaypointsResponse> responseObserver) {
        var httpResponse = delegate.reorderWaypoints(
                UUID.fromString(request.getRouteId()),
                ProtoMapper.toWaypointReorderRequest(request.getWaypointReorderRequest()));
        var builder = WaypointsServiceOuterClass.ReorderWaypointsResponse.newBuilder();
        if (httpResponse.getStatusCode().is2xxSuccessful() && httpResponse.getBody() != null) {
            builder.setWaypointList200(toProtoWaypointList(httpResponse.getBody()));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void updateWaypoint(
            WaypointsServiceOuterClass.UpdateWaypointRequest request,
            StreamObserver<WaypointsServiceOuterClass.UpdateWaypointResponse> responseObserver) {
        var httpResponse = delegate.updateWaypoint(
                UUID.fromString(request.getRouteId()),
                UUID.fromString(request.getWaypointId()),
                ProtoMapper.toWaypointRequest(request.getWaypointRequest()));
        var builder = WaypointsServiceOuterClass.UpdateWaypointResponse.newBuilder();
        if (httpResponse.getStatusCode().is2xxSuccessful() && httpResponse.getBody() != null) {
            builder.setWaypoint200(ProtoMapper.toProtoWaypoint(httpResponse.getBody()));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static WaypointPageOuterClass.WaypointPage toProtoWaypointPage(WaypointPage page) {
        return ProtoMapper.toProtoWaypointPage(page.getContent(), page.getPage());
    }

    private static WaypointListOuterClass.WaypointList toProtoWaypointList(
            com.honeybadger.flightlogistics.model.WaypointList list) {
        return ProtoMapper.toProtoWaypointList(list.getWaypoints());
    }
}
