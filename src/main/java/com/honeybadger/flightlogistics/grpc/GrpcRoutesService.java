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

import com.honeybadger.flightlogistics.api.RoutesApiDelegateImpl;
import com.honeybadger.flightlogistics.model.RoutePage;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import openapitools.RoutePageOuterClass;
import openapitools.services.routesservice.RoutesServiceGrpc;
import openapitools.services.routesservice.RoutesServiceOuterClass;
import org.springframework.grpc.server.service.GrpcService;

import java.util.UUID;

/**
 * gRPC service implementation for route operations.
 *
 * <p>Each RPC is a thin adapter that converts the incoming protobuf request to the
 * OpenAPI model types expected by {@link RoutesApiDelegateImpl}, delegates the call,
 * then maps the {@code ResponseEntity} result back to the appropriate protobuf response
 * {@code oneof} variant. No business logic lives here.
 */
@GrpcService
@RequiredArgsConstructor
public class GrpcRoutesService extends RoutesServiceGrpc.RoutesServiceImplBase {

    private final RoutesApiDelegateImpl delegate;

    @Override
    public void createRoute(
            RoutesServiceOuterClass.CreateRouteRequest request,
            StreamObserver<RoutesServiceOuterClass.CreateRouteResponse> responseObserver) {
        var httpResponse = delegate.createRoute(
                ProtoMapper.toRouteRequest(request.getRouteRequest()));
        var builder = RoutesServiceOuterClass.CreateRouteResponse.newBuilder();
        if (httpResponse.getStatusCode().is2xxSuccessful() && httpResponse.getBody() != null) {
            builder.setRoute201(ProtoMapper.toProtoRoute(httpResponse.getBody()));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void deleteRoute(
            RoutesServiceOuterClass.DeleteRouteRequest request,
            StreamObserver<RoutesServiceOuterClass.DeleteRouteResponse> responseObserver) {
        var httpResponse = delegate.deleteRoute(UUID.fromString(request.getRouteId()));
        var builder = RoutesServiceOuterClass.DeleteRouteResponse.newBuilder();
        if (httpResponse.getStatusCode().is2xxSuccessful()) {
            builder.setEmpty204(Empty.getDefaultInstance());
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getRoute(
            RoutesServiceOuterClass.GetRouteRequest request,
            StreamObserver<RoutesServiceOuterClass.GetRouteResponse> responseObserver) {
        var httpResponse = delegate.getRoute(UUID.fromString(request.getRouteId()));
        var builder = RoutesServiceOuterClass.GetRouteResponse.newBuilder();
        if (httpResponse.getStatusCode().is2xxSuccessful() && httpResponse.getBody() != null) {
            builder.setRoute200(ProtoMapper.toProtoRoute(httpResponse.getBody()));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void listRoutes(
            RoutesServiceOuterClass.ListRoutesRequest request,
            StreamObserver<RoutesServiceOuterClass.ListRoutesResponse> responseObserver) {
        // Proto default (0 = PLANNED) is treated as "no filter"; callers use non-zero for a filter.
        var status = request.getStatusValue() != 0
                ? ProtoMapper.toRouteStatus(request.getStatus())
                : null;
        var sort = request.getSort().isEmpty() ? null : request.getSort();

        var httpResponse = delegate.listRoutes(status, request.getPage(), request.getSize(), sort);
        var builder = RoutesServiceOuterClass.ListRoutesResponse.newBuilder();
        if (httpResponse.getStatusCode().is2xxSuccessful() && httpResponse.getBody() != null) {
            builder.setRoutePage200(toProtoRoutePage(httpResponse.getBody()));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void patchRoute(
            RoutesServiceOuterClass.PatchRouteRequest request,
            StreamObserver<RoutesServiceOuterClass.PatchRouteResponse> responseObserver) {
        var httpResponse = delegate.patchRoute(
                UUID.fromString(request.getRouteId()),
                ProtoMapper.toRoutePatch(request.getRoutePatch()));
        var builder = RoutesServiceOuterClass.PatchRouteResponse.newBuilder();
        if (httpResponse.getStatusCode().is2xxSuccessful() && httpResponse.getBody() != null) {
            builder.setRoute200(ProtoMapper.toProtoRoute(httpResponse.getBody()));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void updateRoute(
            RoutesServiceOuterClass.UpdateRouteRequest request,
            StreamObserver<RoutesServiceOuterClass.UpdateRouteResponse> responseObserver) {
        var httpResponse = delegate.updateRoute(
                UUID.fromString(request.getRouteId()),
                ProtoMapper.toRouteRequest(request.getRouteRequest()));
        var builder = RoutesServiceOuterClass.UpdateRouteResponse.newBuilder();
        if (httpResponse.getStatusCode().is2xxSuccessful() && httpResponse.getBody() != null) {
            builder.setRoute200(ProtoMapper.toProtoRoute(httpResponse.getBody()));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static RoutePageOuterClass.RoutePage toProtoRoutePage(RoutePage page) {
        RoutePageOuterClass.RoutePage.Builder builder =
                RoutePageOuterClass.RoutePage.newBuilder();
        if (page.getContent() != null) {
            page.getContent().stream()
                    .map(ProtoMapper::toProtoRoute)
                    .forEach(builder::addContent);
        }
        builder.setPage(ProtoMapper.toProtoPageMetadata(page.getPage()));
        return builder.build();
    }
}
