#pragma once

#include "datatypes.h"

#include <QMainWindow>
#include <QDockWidget>
#include <QTreeWidget>
#include <QPushButton>
#include <QLabel>
#include <QMap>
#include <memory>
#include <vector>

class MapWidget;
class GrpcClient;

class MainWindow : public QMainWindow {
    Q_OBJECT

public:
    explicit MainWindow(QWidget* parent = nullptr);
    ~MainWindow() override;

private slots:
    void onRefreshRoutes();
    void onTreeSelectionChanged(QTreeWidgetItem* current, QTreeWidgetItem* previous);
    void onRouteItemExpanded(QTreeWidgetItem* item);
    void onResetView();
    void onZoomToRoute();

private:
    void setupUi();
    void setupMenuBar();
    void setUiEnabled(bool enabled);
    void loadRouteWaypoints(QTreeWidgetItem* routeItem);
    void populateRouteTree(const std::vector<RouteInfo>& routes);
    void populateWaypointChildren(QTreeWidgetItem* routeItem,
                                  const std::vector<WaypointInfo>& waypoints);

    // Widgets
    QDockWidget*  routeDock_   = nullptr;
    QTreeWidget*  routeTree_   = nullptr;
    MapWidget*    mapWidget_   = nullptr;
    QPushButton*  refreshBtn_  = nullptr;
    QLabel*       statusHint_  = nullptr;

    // Domain data
    std::unique_ptr<GrpcClient> grpcClient_;
    std::vector<RouteInfo>      routes_;
    QMap<QString, std::vector<WaypointInfo>> waypointCache_;
};
