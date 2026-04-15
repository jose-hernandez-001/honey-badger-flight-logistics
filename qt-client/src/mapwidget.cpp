#include "mapwidget.h"

#include <QPainter>
#include <QPen>
#include <QBrush>
#include <QColor>
#include <QFont>
#include <QResizeEvent>
#include <QMouseEvent>
#include <QWheelEvent>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QUrl>
#include <QUrlQuery>

#include <algorithm>
#include <cmath>
#include <limits>

namespace {
    constexpr double kZoomStep   = 1.25;    // factor per scroll notch
    constexpr double kMinSpanDeg = 0.05;    // minimum viewport span (degrees)
    constexpr int    kMarkerR    = 6;       // waypoint circle radius (px)

    // GeoServer WMS layers in the uk-flight workspace, rendered back-to-front.
    const char* kWmsLayers =
        "uk-flight:ne_10m_land,"
        "uk-flight:ne_10m_admin_1_states_provinces,"
        "uk-flight:ne_10m_admin_0_countries,"
        "uk-flight:ne_10m_coastline";

    // Sea/ocean background colour (WMS BGCOLOR, matches the Qt loading placeholder).
    // Matches the cyan tint used in the web UI map container.
    constexpr const char* kWmsBgColor = "0xa8d8e6";
    const     QColor      kSeaColor(0xa8, 0xd8, 0xe6);

    // Inline SLD that gives land a warm light-brown fill and draws the admin-
    // boundary and coastline layers with thin black strokes, matching the
    // cartographic style of the web UI (which achieves the same via a CSS
    // sepia/hue-rotate filter on transparent WMS tiles).
    const char* kSldBody = R"SLD(
<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0"
  xmlns="http://www.opengis.net/sld"
  xmlns:ogc="http://www.opengis.net/ogc"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>uk-flight:ne_10m_land</Name>
    <UserStyle><FeatureTypeStyle><Rule>
      <PolygonSymbolizer>
        <Fill><CssParameter name="fill">#d9c9a0</CssParameter></Fill>
        <Stroke>
          <CssParameter name="stroke">#c0aa80</CssParameter>
          <CssParameter name="stroke-width">0.5</CssParameter>
        </Stroke>
      </PolygonSymbolizer>
    </Rule></FeatureTypeStyle></UserStyle>
  </NamedLayer>
  <NamedLayer>
    <Name>uk-flight:ne_10m_admin_1_states_provinces</Name>
    <UserStyle><FeatureTypeStyle><Rule>
      <PolygonSymbolizer>
        <Fill><CssParameter name="fill-opacity">0</CssParameter></Fill>
        <Stroke>
          <CssParameter name="stroke">#000000</CssParameter>
          <CssParameter name="stroke-width">0.5</CssParameter>
        </Stroke>
      </PolygonSymbolizer>
    </Rule></FeatureTypeStyle></UserStyle>
  </NamedLayer>
  <NamedLayer>
    <Name>uk-flight:ne_10m_admin_0_countries</Name>
    <UserStyle><FeatureTypeStyle><Rule>
      <PolygonSymbolizer>
        <Fill><CssParameter name="fill-opacity">0</CssParameter></Fill>
        <Stroke>
          <CssParameter name="stroke">#000000</CssParameter>
          <CssParameter name="stroke-width">0.5</CssParameter>
        </Stroke>
      </PolygonSymbolizer>
    </Rule></FeatureTypeStyle></UserStyle>
  </NamedLayer>
  <NamedLayer>
    <Name>uk-flight:ne_10m_coastline</Name>
    <UserStyle><FeatureTypeStyle><Rule>
      <LineSymbolizer>
        <Stroke>
          <CssParameter name="stroke">#000000</CssParameter>
          <CssParameter name="stroke-width">0.5</CssParameter>
        </Stroke>
      </LineSymbolizer>
    </Rule></FeatureTypeStyle></UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
)SLD";
}

// ─────────────────────────────────────────────────────────────────────────────

MapWidget::MapWidget(QWidget* parent)
    : QWidget(parent)
    , nam_(new QNetworkAccessManager(this))
{
    setMouseTracking(false);   // only track while a button is held
    setMinimumSize(400, 300);
    connect(nam_, &QNetworkAccessManager::finished,
            this, &MapWidget::onMapReplyFinished);
}

// ─────────────────────────────────────────────────────────────────────────────
// Public API
// ─────────────────────────────────────────────────────────────────────────────

void MapWidget::setWmsUrl(const QString& url)
{
    wmsUrl_ = url;
    fetchMap();
}

void MapWidget::setWaypoints(const std::vector<WaypointInfo>& waypoints)
{
    waypoints_ = waypoints;
    update();
}

void MapWidget::zoomToWaypoints()
{
    if (waypoints_.empty()) return;

    double minLon =  std::numeric_limits<double>::max();
    double minLat =  std::numeric_limits<double>::max();
    double maxLon = -std::numeric_limits<double>::max();
    double maxLat = -std::numeric_limits<double>::max();

    for (const auto& wp : waypoints_) {
        minLon = std::min(minLon, wp.longitude);
        minLat = std::min(minLat, wp.latitude);
        maxLon = std::max(maxLon, wp.longitude);
        maxLat = std::max(maxLat, wp.latitude);
    }

    // Ensure a minimum span so a single waypoint doesn't produce a zero box.
    const double lonSpan = std::max(maxLon - minLon, kMinSpanDeg);
    const double latSpan = std::max(maxLat - minLat, kMinSpanDeg);
    const double padLon  = lonSpan * 0.25;
    const double padLat  = latSpan * 0.25;

    viewMinLon_ = minLon - padLon;
    viewMaxLon_ = maxLon + padLon;
    viewMinLat_ = minLat - padLat;
    viewMaxLat_ = maxLat + padLat;

    fetchMap();
}

void MapWidget::resetView()
{
    viewMinLon_ = -12.0;
    viewMinLat_ =  49.0;
    viewMaxLon_ =   4.0;
    viewMaxLat_ =  62.0;
    fetchMap();
}

// ─────────────────────────────────────────────────────────────────────────────
// Networking
// ─────────────────────────────────────────────────────────────────────────────

void MapWidget::fetchMap()
{
    if (width() < 10 || height() < 10) return;

    if (pendingReply_) {
        // Clear the member BEFORE abort() because abort() emits finished()
        // synchronously, which calls onMapReplyFinished() and sets
        // pendingReply_ = nullptr.  Calling deleteLater() on that null member
        // afterwards is the segfault we are preventing here.
        QNetworkReply* old = pendingReply_;
        pendingReply_ = nullptr;
        old->abort();
        old->deleteLater();
    }

    QUrlQuery q;
    q.addQueryItem("SERVICE",     "WMS");
    q.addQueryItem("VERSION",     "1.1.1");
    q.addQueryItem("REQUEST",     "GetMap");
    q.addQueryItem("LAYERS",      QLatin1String(kWmsLayers));
    // Correct the lon extent for the cos(lat) factor so the requested tile
    // has square pixels in real-world terms (EPSG:4326, WMS 1.1.1).
    double adjMinLon, adjMaxLon;
    correctedLonRange(adjMinLon, adjMaxLon);
    q.addQueryItem("BBOX",        QString("%1,%2,%3,%4")
                                      .arg(adjMinLon,   0, 'f', 6)
                                      .arg(viewMinLat_, 0, 'f', 6)
                                      .arg(adjMaxLon,   0, 'f', 6)
                                      .arg(viewMaxLat_, 0, 'f', 6));
    q.addQueryItem("WIDTH",       QString::number(width()));
    q.addQueryItem("HEIGHT",      QString::number(height()));
    q.addQueryItem("SRS",         "EPSG:4326");
    q.addQueryItem("FORMAT",      "image/png");
    q.addQueryItem("TRANSPARENT", "FALSE");
    q.addQueryItem("BGCOLOR",     QLatin1String(kWmsBgColor));
    q.addQueryItem("SLD_BODY",    QLatin1String(kSldBody));

    QUrl url(wmsUrl_);
    url.setQuery(q);

    mapLoading_   = true;
    pendingReply_ = nam_->get(QNetworkRequest(url));
}

void MapWidget::onMapReplyFinished(QNetworkReply* reply)
{
    mapLoading_   = false;
    pendingReply_ = nullptr;

    if (reply->error() != QNetworkReply::NoError) {
        reply->deleteLater();
        update();   // repaint placeholder
        return;
    }

    const QByteArray data = reply->readAll();
    reply->deleteLater();

    QPixmap px;
    if (px.loadFromData(data)) {
        mapImage_ = std::move(px);
    }
    update();
}

// ─────────────────────────────────────────────────────────────────────────────
// Coordinate helpers
// ─────────────────────────────────────────────────────────────────────────────

// Computes the longitude range adjusted so that one pixel represents the same
// real-world distance in both X and Y.  In EPSG:4326 the raw degree span must
// be divided by cos(midLat) to account for meridian convergence.
void MapWidget::correctedLonRange(double& outMin, double& outMax) const
{
    const double midLat  = (viewMinLat_ + viewMaxLat_) * 0.5;
    const double latSpan = viewMaxLat_ - viewMinLat_;
    const double midLon  = (viewMinLon_ + viewMaxLon_) * 0.5;
    const double lonSpan = latSpan * double(width()) / double(height())
                           / std::cos(midLat * M_PI / 180.0);
    outMin = midLon - lonSpan * 0.5;
    outMax = midLon + lonSpan * 0.5;
}

QPointF MapWidget::geoToPixel(double lat, double lon) const
{
    // Use the same corrected lon range as fetchMap() so waypoint markers
    // align precisely with the WMS tile.
    double adjMinLon, adjMaxLon;
    correctedLonRange(adjMinLon, adjMaxLon);
    const double xFrac = (lon - adjMinLon) / (adjMaxLon - adjMinLon);
    const double yFrac = (viewMaxLat_ - lat) / (viewMaxLat_ - viewMinLat_);  // y flipped
    return { xFrac * width(), yFrac * height() };
}

// ─────────────────────────────────────────────────────────────────────────────
// Paint
// ─────────────────────────────────────────────────────────────────────────────

void MapWidget::paintEvent(QPaintEvent*)
{
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing);
    p.setRenderHint(QPainter::SmoothPixmapTransform);

    // ── Map background ────────────────────────────────────────────────────
    if (!mapImage_.isNull()) {
        p.drawPixmap(rect(), mapImage_);
    } else {
        p.fillRect(rect(), kSeaColor);
        p.setPen(Qt::darkGray);
        const QString msg = mapLoading_
            ? tr("Loading map…")
            : tr("Map unavailable\n(Is GeoServer running on port 8600?)");
        p.drawText(rect(), Qt::AlignCenter, msg);
    }

    if (waypoints_.empty()) return;

    // ── Route polyline ────────────────────────────────────────────────────
    {
        QPen linePen(QColor(30, 100, 210), 2.5, Qt::SolidLine,
                     Qt::RoundCap, Qt::RoundJoin);
        p.setPen(linePen);
        p.setBrush(Qt::NoBrush);

        QPolygonF poly;
        poly.reserve(static_cast<int>(waypoints_.size()));
        for (const auto& wp : waypoints_) {
            poly << geoToPixel(wp.latitude, wp.longitude);
        }
        p.drawPolyline(poly);
    }

    // ── Waypoint markers ─────────────────────────────────────────────────
    QFont labelFont = p.font();
    labelFont.setPointSize(8);
    p.setFont(labelFont);

    const int n = static_cast<int>(waypoints_.size());
    for (int i = 0; i < n; ++i) {
        const auto& wp     = waypoints_[i];
        const QPointF ctr  = geoToPixel(wp.latitude, wp.longitude);

        // Colour: green for first, red for last, blue for all others.
        const QColor fill =
            (i == 0)     ? QColor(30, 160, 30) :
            (i == n - 1) ? QColor(200, 30, 30) :
                           QColor(30, 100, 210);

        p.setPen(QPen(Qt::white, 1.5));
        p.setBrush(fill);
        p.drawEllipse(ctr, kMarkerR, kMarkerR);

        // Sequence number centred inside the circle.
        p.setPen(Qt::white);
        p.drawText(
            QRectF(ctr.x() - kMarkerR, ctr.y() - kMarkerR,
                   kMarkerR * 2, kMarkerR * 2),
            Qt::AlignCenter,
            QString::number(wp.sequence));

        // Waypoint name label to the right of the circle.
        if (!wp.name.empty()) {
            p.setPen(QPen(Qt::black, 1));
            p.drawText(
                QPointF(ctr.x() + kMarkerR + 4, ctr.y() + 4),
                QString::fromStdString(wp.name));
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Interaction
// ─────────────────────────────────────────────────────────────────────────────

void MapWidget::resizeEvent(QResizeEvent*)
{
    fetchMap();
}

void MapWidget::mousePressEvent(QMouseEvent* event)
{
    if (event->button() == Qt::LeftButton) {
        panning_      = true;
        lastMousePos_ = event->pos();
        setCursor(Qt::ClosedHandCursor);
        setMouseTracking(true);
    }
}

void MapWidget::mouseMoveEvent(QMouseEvent* event)
{
    if (!panning_) return;

    const QPoint delta = event->pos() - lastMousePos_;
    lastMousePos_      = event->pos();

    const double lonPerPx = (viewMaxLon_ - viewMinLon_) / width();
    const double latPerPx = (viewMaxLat_ - viewMinLat_) / height();

    // Pan speed must use the corrected lon range so dragging feels 1:1 on screen.
    double adjMinLon, adjMaxLon;
    correctedLonRange(adjMinLon, adjMaxLon);
    const double corrLonPerPx = (adjMaxLon - adjMinLon) / width();

    const double lonDelta = -delta.x() * corrLonPerPx;
    const double latDelta =  delta.y() * latPerPx;  // y axis is inverted
    viewMinLon_ += lonDelta;  viewMaxLon_ += lonDelta;
    viewMinLat_ += latDelta;  viewMaxLat_ += latDelta;

    // Repaint markers immediately with the stale map image while the new
    // tile is in flight; the image will be re-placed once fetchMap() returns.
    update();
}

void MapWidget::mouseReleaseEvent(QMouseEvent* event)
{
    if (event->button() == Qt::LeftButton && panning_) {
        panning_ = false;
        setMouseTracking(false);
        setCursor(Qt::ArrowCursor);
        fetchMap();
    }
}

void MapWidget::wheelEvent(QWheelEvent* event)
{
    const bool zoomIn  = event->angleDelta().y() > 0;
    const double factor = zoomIn ? (1.0 / kZoomStep) : kZoomStep;

    // Keep the geographic point under the cursor fixed.
    const double cx     = event->position().x();
    const double cy     = event->position().y();
    const double fracX  = cx / width();
    const double fracY  = cy / height();

    // Use the corrected lon range so the cursor pins the right geographic point.
    double adjMinLon, adjMaxLon;
    correctedLonRange(adjMinLon, adjMaxLon);
    const double curLon = adjMinLon + fracX * (adjMaxLon - adjMinLon);
    const double curLat = viewMaxLat_ - fracY * (viewMaxLat_ - viewMinLat_);

    // Zoom latSpan; lonSpan is always derived via correctedLonRange.
    const double newLatSpan = std::max((viewMaxLat_ - viewMinLat_) * factor, kMinSpanDeg);
    viewMinLat_ = curLat - (1.0 - fracY) * newLatSpan;
    viewMaxLat_ = viewMinLat_ + newLatSpan;

    // Recompute the corrected lon span at the new latitude and pin curLon to fracX.
    const double newMidLat      = (viewMinLat_ + viewMaxLat_) * 0.5;
    const double newCorrLonSpan = newLatSpan * double(width()) / double(height())
                                  / std::cos(newMidLat * M_PI / 180.0);
    const double newMidLon = curLon + newCorrLonSpan * (0.5 - fracX);
    viewMinLon_ = newMidLon - newCorrLonSpan * 0.5;
    viewMaxLon_ = newMidLon + newCorrLonSpan * 0.5;

    fetchMap();
}
