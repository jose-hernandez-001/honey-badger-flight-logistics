#include "mainwindow.h"
#include "grpcclient.h"
#include "mapwidget.h"

#include <QApplication>
#include <QDockWidget>
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QHeaderView>
#include <QStatusBar>
#include <QMenuBar>
#include <QMenu>
#include <QAction>
#include <QDialog>
#include <QDialogButtonBox>
#include <QPixmap>
#include <QMessageBox>
#include <QTimer>
#include <QThreadPool>
#include <QTreeWidget>

static const QString kDefaultGrpcAddress = QStringLiteral("localhost:9090");
static const QString kDefaultWmsUrl =
    QStringLiteral("http://127.0.0.1:8600/geoserver/wms");

// ─────────────────────────────────────────────────────────────────────────────

MainWindow::MainWindow(QWidget* parent)
    : QMainWindow(parent)
    , grpcClient_(std::make_unique<GrpcClient>(kDefaultGrpcAddress.toStdString()))
{
    setupUi();
    setupMenuBar();
    statusBar()->showMessage(
        tr("gRPC: %1   WMS: %2").arg(kDefaultGrpcAddress, kDefaultWmsUrl));

    // Load routes once the event loop is running so the window is visible first.
    QTimer::singleShot(0, this, &MainWindow::onRefreshRoutes);
}

MainWindow::~MainWindow() = default;

// ─────────────────────────────────────────────────────────────────────────────
// UI construction
// ─────────────────────────────────────────────────────────────────────────────

void MainWindow::setupUi()
{
    setWindowTitle(tr("Honey Badger Flight Logistics"));
    resize(1300, 820);

    // ── Route dock (floating / dockable) ─────────────────────────────────────
    auto* dockContents = new QWidget;
    auto* leftLayout   = new QVBoxLayout(dockContents);
    leftLayout->setContentsMargins(4, 6, 4, 6);
    leftLayout->setSpacing(6);

    auto* routesTitle = new QLabel(tr("<b>Routes</b>"));
    routesTitle->setAlignment(Qt::AlignLeft | Qt::AlignVCenter);

    routeTree_ = new QTreeWidget;
    routeTree_->setColumnCount(3);
    routeTree_->setHeaderLabels({tr("Name"), tr("Status / Position"), tr("Info")});
    routeTree_->header()->setStretchLastSection(false);
    routeTree_->header()->setSectionResizeMode(0, QHeaderView::Stretch);
    routeTree_->header()->setSectionResizeMode(1, QHeaderView::ResizeToContents);
    routeTree_->header()->setSectionResizeMode(2, QHeaderView::ResizeToContents);
    routeTree_->setSelectionMode(QAbstractItemView::SingleSelection);
    routeTree_->setAlternatingRowColors(true);
    routeTree_->setMinimumWidth(300);
    routeTree_->setAnimated(true);
    routeTree_->setRootIsDecorated(true);
    routeTree_->setIndentation(18);
    routeTree_->setUniformRowHeights(true);

    refreshBtn_ = new QPushButton(tr("\u27f3  Refresh routes"));

    statusHint_ = new QLabel(tr("Select a route to view its waypoints on the map."));
    statusHint_->setAlignment(Qt::AlignCenter);
    statusHint_->setWordWrap(true);
    statusHint_->setStyleSheet(QStringLiteral("color: #666; font-style: italic;"));

    leftLayout->addWidget(routesTitle);
    leftLayout->addWidget(routeTree_, 1);
    leftLayout->addWidget(refreshBtn_);
    leftLayout->addWidget(statusHint_);

    routeDock_ = new QDockWidget(tr("Routes"), this);
    routeDock_->setWidget(dockContents);
    routeDock_->setAllowedAreas(Qt::LeftDockWidgetArea | Qt::RightDockWidgetArea);
    routeDock_->setFeatures(QDockWidget::DockWidgetMovable  |
                             QDockWidget::DockWidgetFloatable |
                             QDockWidget::DockWidgetClosable);
    addDockWidget(Qt::LeftDockWidgetArea, routeDock_);

    // ── Central widget: map with a small margin ───────────────────────────────
    auto* centralContainer = new QWidget;
    auto* centralLayout    = new QVBoxLayout(centralContainer);
    centralLayout->setContentsMargins(3, 3, 3, 3);
    centralLayout->setSpacing(0);

    mapWidget_ = new MapWidget;
    mapWidget_->setMinimumSize(600, 380);
    mapWidget_->setWmsUrl(kDefaultWmsUrl);
    mapWidget_->resetView();

    centralLayout->addWidget(mapWidget_);
    setCentralWidget(centralContainer);

    // ── Connections ──────────────────────────────────────────────────────────
    connect(refreshBtn_, &QPushButton::clicked,
            this, &MainWindow::onRefreshRoutes);
    connect(routeTree_, &QTreeWidget::currentItemChanged,
            this, &MainWindow::onTreeSelectionChanged);
    connect(routeTree_, &QTreeWidget::itemExpanded,
            this, &MainWindow::onRouteItemExpanded);
}

void MainWindow::setupMenuBar()
{
    QMenu* viewMenu = menuBar()->addMenu(tr("&View"));
    viewMenu->addAction(routeDock_->toggleViewAction());
    viewMenu->addSeparator();
    viewMenu->addAction(tr("&Reset map to UK"),
                        QKeySequence(Qt::CTRL | Qt::Key_R),
                        this, &MainWindow::onResetView);
    viewMenu->addAction(tr("&Zoom to route waypoints"),
                        QKeySequence(Qt::CTRL | Qt::Key_Z),
                        this, &MainWindow::onZoomToRoute);
    viewMenu->addSeparator();
    viewMenu->addAction(tr("&Quit"),
                        QKeySequence::Quit,
                        qApp, &QCoreApplication::quit);

    QMenu* helpMenu = menuBar()->addMenu(tr("&Help"));
    helpMenu->addAction(tr("&About"), this, [this] {
        auto* dlg = new QDialog(this);
        dlg->setWindowTitle(tr("About Honey Badger Flight Logistics"));
        dlg->setAttribute(Qt::WA_DeleteOnClose);

        // Airplane image – scale to 128 px tall, keep aspect ratio, smooth
        QPixmap px(QStringLiteral(":/resources/airplane.png"));
        px = px.scaledToHeight(128, Qt::SmoothTransformation);

        auto* imgLabel = new QLabel;
        imgLabel->setPixmap(px);
        imgLabel->setAlignment(Qt::AlignTop | Qt::AlignHCenter);
        // Transparent background so the window colour shows through
        imgLabel->setAttribute(Qt::WA_TranslucentBackground);
        imgLabel->setStyleSheet(QStringLiteral("background: transparent;"));

        auto* textLabel = new QLabel(
            tr("<b>Honey Badger Flight Logistics</b><br/>"
               "Version 1.0.0<br/><br/>"
               "Qt desktop client for managing flight routes.<br/><br/>"
               "Connects to the gRPC server on <tt>localhost:9090</tt> and "
               "shows routes on a GeoServer WMS map (<tt>:8600</tt>).<br/><br/>"
               "&copy; 2026 Jose Hernandez &mdash; "
               "<a href=\"https://opensource.org/licenses/MIT\">MIT License</a>"));
        textLabel->setTextFormat(Qt::RichText);
        textLabel->setAlignment(Qt::AlignTop | Qt::AlignLeft);
        textLabel->setWordWrap(true);
        textLabel->setOpenExternalLinks(true);

        auto* btns = new QDialogButtonBox(QDialogButtonBox::Ok);
        connect(btns, &QDialogButtonBox::accepted, dlg, &QDialog::accept);

        auto* row = new QHBoxLayout;
        row->setSpacing(16);
        row->addWidget(imgLabel, 0, Qt::AlignTop);
        row->addWidget(textLabel, 1);

        auto* root = new QVBoxLayout(dlg);
        root->setContentsMargins(16, 16, 16, 12);
        root->setSpacing(12);
        root->addLayout(row);
        root->addWidget(btns);

        dlg->exec();
    });
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

void MainWindow::setUiEnabled(bool enabled)
{
    refreshBtn_->setEnabled(enabled);
    routeTree_->setEnabled(enabled);
}

// ─────────────────────────────────────────────────────────────────────────────
// Slots
// ─────────────────────────────────────────────────────────────────────────────

void MainWindow::onRefreshRoutes()
{
    setUiEnabled(false);
    statusBar()->showMessage(tr("Loading routes…"));

    QThreadPool::globalInstance()->start([this] {
        std::vector<RouteInfo> loaded;
        std::string            errMsg;
        const bool ok = grpcClient_->listRoutes(loaded, errMsg);

        QMetaObject::invokeMethod(this,
            [this, ok, loaded = std::move(loaded), errMsg] {
                setUiEnabled(true);
                if (ok) {
                    routes_ = loaded;
                    populateRouteTree(loaded);
                    statusBar()->showMessage(
                        tr("%1 route(s) loaded.").arg(loaded.size()));
                } else {
                    statusBar()->showMessage(
                        tr("Failed to load routes: %1")
                            .arg(QString::fromStdString(errMsg)));
                }
            },
            Qt::QueuedConnection);
    });
}

void MainWindow::onTreeSelectionChanged(QTreeWidgetItem* current,
                                        QTreeWidgetItem* /*previous*/)
{
    if (!current) return;

    if (current->parent() == nullptr) {
        // ── Route item selected ──────────────────────────────────────────────
        const QString routeId = current->data(0, Qt::UserRole).toString();
        if (waypointCache_.contains(routeId)) {
            const auto& wps = waypointCache_[routeId];
            populateWaypointChildren(current, wps);
            current->setExpanded(true);
            mapWidget_->setWaypoints(wps);
            if (!wps.empty()) mapWidget_->zoomToWaypoints();
            statusBar()->showMessage(tr("%1 waypoint(s).").arg(wps.size()));
        } else {
            loadRouteWaypoints(current);
        }
    } else {
        // ── Waypoint child item selected ─────────────────────────────────────
        QTreeWidgetItem* routeItem = current->parent();
        const QString routeId = routeItem->data(0, Qt::UserRole).toString();
        if (waypointCache_.contains(routeId)) {
            const auto& wps = waypointCache_[routeId];
            mapWidget_->setWaypoints(wps);
            if (!wps.empty()) mapWidget_->zoomToWaypoints();
        }
    }
}

void MainWindow::onRouteItemExpanded(QTreeWidgetItem* item)
{
    if (item->parent() != nullptr) return; // only top-level route items

    const QString routeId = item->data(0, Qt::UserRole).toString();
    if (waypointCache_.contains(routeId)) {
        populateWaypointChildren(item, waypointCache_[routeId]);
    } else {
        loadRouteWaypoints(item);
    }
}

void MainWindow::loadRouteWaypoints(QTreeWidgetItem* routeItem)
{
    // Guard against concurrent loads for the same item.
    if (routeItem->data(0, Qt::UserRole + 2).toBool()) return;
    routeItem->setData(0, Qt::UserRole + 2, true);

    // Replace placeholder with a loading indicator.
    while (routeItem->childCount() > 0)
        delete routeItem->takeChild(0);
    auto* loadingItem = new QTreeWidgetItem(routeItem);
    loadingItem->setText(0, tr("Loading waypoints\u2026"));
    loadingItem->setFlags(Qt::ItemIsEnabled);
    routeItem->setExpanded(true);

    const QString routeId = routeItem->data(0, Qt::UserRole).toString();
    statusBar()->showMessage(tr("Loading waypoints\u2026"));
    setUiEnabled(false);

    QThreadPool::globalInstance()->start(
        [this, routeId = routeId.toStdString(), routeItem] {
            std::vector<WaypointInfo> wps;
            std::string               errMsg;
            const bool ok = grpcClient_->listWaypoints(routeId, wps, errMsg);

            QMetaObject::invokeMethod(this,
                [this, ok, wps = std::move(wps), errMsg,
                 qRouteId = QString::fromStdString(routeId), routeItem] {
                    setUiEnabled(true);
                    routeItem->setData(0, Qt::UserRole + 2, false);

                    while (routeItem->childCount() > 0)
                        delete routeItem->takeChild(0);

                    if (ok) {
                        waypointCache_[qRouteId] = wps;
                        populateWaypointChildren(routeItem, wps);

                        // Update map + table only when this route is selected.
                        QTreeWidgetItem* sel = routeTree_->currentItem();
                        const bool routeSelected =
                            sel == routeItem ||
                            (sel && sel->parent() == routeItem);
                        if (routeSelected) {
                            mapWidget_->setWaypoints(wps);
                            if (!wps.empty()) mapWidget_->zoomToWaypoints();
                        }
                        statusBar()->showMessage(
                            tr("%1 waypoint(s) loaded.").arg(wps.size()));
                    } else {
                        auto* errItem = new QTreeWidgetItem(routeItem);
                        errItem->setText(0, tr("Error: %1")
                                             .arg(QString::fromStdString(errMsg)));
                        errItem->setForeground(0, Qt::red);
                        errItem->setFlags(Qt::ItemIsEnabled);
                        statusBar()->showMessage(
                            tr("Failed to load waypoints: %1")
                                .arg(QString::fromStdString(errMsg)));
                    }
                },
                Qt::QueuedConnection);
        });
}

void MainWindow::onResetView()
{
    mapWidget_->resetView();
}

void MainWindow::onZoomToRoute()
{
    mapWidget_->zoomToWaypoints();
}

// ─────────────────────────────────────────────────────────────────────────────
// Model → View
// ─────────────────────────────────────────────────────────────────────────────

void MainWindow::populateRouteTree(const std::vector<RouteInfo>& routes)
{
    routeTree_->clear();
    waypointCache_.clear();

    static const QMap<QString, QColor> kStatusColour = {
        {"Planned",   QColor("#808080")},
        {"Active",    QColor("#1a7a1a")},
        {"Paused",    QColor("#b08000")},
        {"Completed", QColor("#1a5fa0")},
        {"Aborted",   QColor("#a01a1a")},
    };

    for (const auto& r : routes) {
        const QString name   = QString::fromStdString(r.name);
        const QString status = QString::fromStdString(r.status);
        const QColor  colour = kStatusColour.value(status, QColor("#606060"));

        auto* item = new QTreeWidgetItem(routeTree_);
        item->setText(0, name);
        item->setText(1, status);
        item->setText(2, tr("%1 wpt(s)").arg(r.waypointCount));
        item->setForeground(1, colour);
        item->setData(0, Qt::UserRole, QString::fromStdString(r.id));

        // Placeholder child so the expand arrow is shown.
        if (r.waypointCount > 0) {
            auto* ph = new QTreeWidgetItem(item);
            ph->setText(0, tr("Select or expand to load waypoints…"));
            ph->setForeground(0, QColor("#888"));
            ph->setFlags(Qt::ItemIsEnabled); // not selectable
        }
    }

    statusHint_->setText(
        routes.empty()
            ? tr("No routes found on the server.")
            : tr("%1 route(s) — select or expand to load waypoints.")
                  .arg(routes.size()));
}

void MainWindow::populateWaypointChildren(QTreeWidgetItem* routeItem,
                                          const std::vector<WaypointInfo>& wps)
{
    // Skip if already populated with real waypoint items.
    if (routeItem->childCount() > 0 &&
        routeItem->child(0)->data(0, Qt::UserRole + 1).toBool())
        return;

    while (routeItem->childCount() > 0)
        delete routeItem->takeChild(0);

    for (const auto& wp : wps) {
        auto* child = new QTreeWidgetItem(routeItem);
        child->setText(0, QString("%1.  %2")
                           .arg(wp.sequence)
                           .arg(QString::fromStdString(wp.name)));
        child->setText(1, QString("%1,  %2")
                           .arg(wp.latitude,  0, 'f', 4)
                           .arg(wp.longitude, 0, 'f', 4));
        child->setText(2, QString("%1 m  ·  %2 kn")
                           .arg(wp.altitude, 0, 'f', 0)
                           .arg(wp.speed,    0, 'f', 0));
        child->setData(0, Qt::UserRole,     wp.sequence);
        child->setData(0, Qt::UserRole + 1, true); // is real waypoint
    }
}
