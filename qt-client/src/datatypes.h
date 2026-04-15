#pragma once

#include <string>

// Plain data structs shared between GrpcClient, MainWindow and MapWidget.
// No Qt or gRPC dependencies.

struct RouteInfo {
    std::string id;
    std::string name;
    std::string description;
    std::string aircraftId;
    std::string status;       // human-readable: "Planned", "Active", …
    int         waypointCount = 0;
};

struct WaypointInfo {
    std::string id;
    std::string name;
    int         sequence  = 0;
    double      latitude  = 0.0;
    double      longitude = 0.0;
    double      altitude  = 0.0;  // metres AMSL
    double      speed     = 0.0;  // knots
    double      heading   = 0.0;  // degrees
    int         holdTime  = 0;    // seconds
};
