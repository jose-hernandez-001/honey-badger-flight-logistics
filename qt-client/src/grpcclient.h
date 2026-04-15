#pragma once

#include "datatypes.h"

#include <memory>
#include <string>
#include <vector>

#include <grpcpp/grpcpp.h>
#include "services/routes_service.grpc.pb.h"
#include "services/waypoints_service.grpc.pb.h"

// Thread-safe gRPC wrapper.  All public methods block the calling thread
// until the RPC completes; call them from a worker thread, not the GUI thread.
class GrpcClient {
public:
    explicit GrpcClient(const std::string& serverAddress = "localhost:9090");

    // Fills |routes| with all routes (up to 200).
    // Returns false and sets |error| on failure.
    bool listRoutes(std::vector<RouteInfo>& routes, std::string& error);

    // Fills |waypoints| for the given route, sorted by sequence number.
    // Returns false and sets |error| on failure.
    bool listWaypoints(const std::string& routeId,
                       std::vector<WaypointInfo>& waypoints,
                       std::string& error);

private:
    static std::string statusLabel(openapitools::RouteStatus s);

    std::shared_ptr<grpc::Channel> channel_;
    std::unique_ptr<openapitools::services::routesservice::RoutesService::Stub>
        routesStub_;
    std::unique_ptr<openapitools::services::waypointsservice::WaypointsService::Stub>
        waypointsStub_;
};
