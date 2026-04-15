#pragma once

#include "datatypes.h"

#include <QWidget>
#include <QPixmap>
#include <QPoint>
#include <vector>

class QNetworkAccessManager;
class QNetworkReply;

// MapWidget renders a WMS map tile fetched from GeoServer and overlays
// flight-route waypoints on top of it.
//
// Interaction:
//   • Left-drag   – pan the view
//   • Scroll wheel – zoom in / out centred on the cursor
//   • Ctrl+R       – reset to the default UK bounding box  (via resetView())
//   • Ctrl+Z       – zoom to fit all current waypoints     (via zoomToWaypoints())
class MapWidget : public QWidget {
    Q_OBJECT

public:
    explicit MapWidget(QWidget* parent = nullptr);

    void setWmsUrl(const QString& url);

    // Replace the displayed waypoints and redraw immediately.
    // Call zoomToWaypoints() afterwards to adjust the viewport.
    void setWaypoints(const std::vector<WaypointInfo>& waypoints);

    // Adjust the viewport so that all current waypoints fit with 20 % padding,
    // then fetch a fresh map tile.
    void zoomToWaypoints();

    // Reset the viewport to the default UK bounding box and fetch a new tile.
    void resetView();

protected:
    void paintEvent(QPaintEvent*)   override;
    void resizeEvent(QResizeEvent*) override;
    void mousePressEvent(QMouseEvent*)   override;
    void mouseMoveEvent(QMouseEvent*)    override;
    void mouseReleaseEvent(QMouseEvent*) override;
    void wheelEvent(QWheelEvent*)   override;

private slots:
    void onMapReplyFinished(QNetworkReply* reply);

private:
    void    fetchMap();
    QPointF geoToPixel(double lat, double lon) const;

    // Returns the longitude range corrected for the cos(lat) factor so that
    // one pixel represents the same real-world distance in both axes.
    void correctedLonRange(double& outMin, double& outMax) const;

    QNetworkAccessManager* nam_           = nullptr;
    QNetworkReply*         pendingReply_  = nullptr;

    QString wmsUrl_ = QStringLiteral("http://127.0.0.1:8600/geoserver/wms");

    // Current geographic bounding box (EPSG:4326)
    double viewMinLon_ = -12.0;
    double viewMinLat_ =  49.0;
    double viewMaxLon_ =   4.0;
    double viewMaxLat_ =  62.0;

    QPixmap mapImage_;
    bool    mapLoading_ = false;

    std::vector<WaypointInfo> waypoints_;

    // Pan state
    bool   panning_      = false;
    QPoint lastMousePos_;
};
