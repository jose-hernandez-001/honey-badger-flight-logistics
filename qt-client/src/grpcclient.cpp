#include "grpcclient.h"

#include <algorithm>

using namespace openapitools;
using namespace openapitools::services::routesservice;
using namespace openapitools::services::waypointsservice;

// ─────────────────────────────────────────────────────────────────────────────

GrpcClient::GrpcClient(const std::string& serverAddress)
    : channel_(grpc::CreateChannel(serverAddress,
                                   grpc::InsecureChannelCredentials()))
    , routesStub_(RoutesService::NewStub(channel_))
    , waypointsStub_(WaypointsService::NewStub(channel_))
{}

// ─────────────────────────────────────────────────────────────────────────────

bool GrpcClient::listRoutes(std::vector<RouteInfo>& routes,
                            std::string& error)
{
    ListRoutesRequest req;
    req.set_page(0);
    req.set_size(200);

    ListRoutesResponse resp;
    grpc::ClientContext ctx;
    const grpc::Status st = routesStub_->ListRoutes(&ctx, req, &resp);

    if (!st.ok()) {
        error = st.error_message();
        return false;
    }

    switch (resp.response_case()) {
    case ListRoutesResponse::kRoutePage200:
        for (const Route& r : resp.route_page_200().content()) {
            RouteInfo info;
            info.id            = r.id();
            info.name          = r.name();
            info.description   = r.description();
            info.aircraftId    = r.aircraft_id();
            info.status        = statusLabel(r.status());
            info.waypointCount = r.waypoint_count();
            routes.push_back(std::move(info));
        }
        return true;

    case ListRoutesResponse::kProblemDetail400:
        error = resp.problem_detail_400().detail();
        return false;

    case ListRoutesResponse::kProblemDetail500:
        error = resp.problem_detail_500().detail();
        return false;

    default:
        return true;   // RESPONSE_NOT_SET → empty result is valid
    }
}

// ─────────────────────────────────────────────────────────────────────────────

bool GrpcClient::listWaypoints(const std::string& routeId,
                                std::vector<WaypointInfo>& waypoints,
                                std::string& error)
{
    ListWaypointsRequest req;
    req.set_route_id(routeId);
    req.set_page(0);
    req.set_size(1000);

    ListWaypointsResponse resp;
    grpc::ClientContext ctx;
    const grpc::Status st = waypointsStub_->ListWaypoints(&ctx, req, &resp);

    if (!st.ok()) {
        error = st.error_message();
        return false;
    }

    switch (resp.response_case()) {
    case ListWaypointsResponse::kWaypointPage200:
        for (const Waypoint& w : resp.waypoint_page_200().content()) {
            WaypointInfo info;
            info.id        = w.id();
            info.name      = w.name();
            info.sequence  = w.sequence();
            info.latitude  = w.latitude();
            info.longitude = w.longitude();
            info.altitude  = w.altitude();
            info.speed     = w.speed();
            info.heading   = w.heading();
            info.holdTime  = w.hold_time();
            waypoints.push_back(std::move(info));
        }
        std::sort(waypoints.begin(), waypoints.end(),
                  [](const WaypointInfo& a, const WaypointInfo& b) {
                      return a.sequence < b.sequence;
                  });
        return true;

    case ListWaypointsResponse::kProblemDetail404:
        error = resp.problem_detail_404().detail();
        return false;

    case ListWaypointsResponse::kProblemDetail500:
        error = resp.problem_detail_500().detail();
        return false;

    default:
        return true;
    }
}

// ─────────────────────────────────────────────────────────────────────────────

std::string GrpcClient::statusLabel(RouteStatus s)
{
    switch (s) {
    case ROUTE_STATUS_PLANNED:   return "Planned";
    case ROUTE_STATUS_ACTIVE:    return "Active";
    case ROUTE_STATUS_PAUSED:    return "Paused";
    case ROUTE_STATUS_COMPLETED: return "Completed";
    case ROUTE_STATUS_ABORTED:   return "Aborted";
    default:                     return "Unknown";
    }
}
