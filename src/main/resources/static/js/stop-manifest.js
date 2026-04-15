'use strict';

(function () {

  // ── Private helpers ────────────────────────────────────────────────────────

  function escHtml(s) {
    return String(s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function fmt(n) {
    return n != null ? parseFloat(n).toFixed(4).replace(/\.?0+$/, '') : '—';
  }

  // ── Custom element ─────────────────────────────────────────────────────────
  //
  // Modes
  //   detail (default) – used in route.html: sort controls, no per-row actions
  //   list             – used in routes.html: stop count + Add Stop button,
  //                      edit/delete per row, no sort controls
  //
  // Attributes (read once on connectedCallback)
  //   data-mode           "detail" | "list"
  //   data-route-id     route UUID (list mode; detail mode uses setRoute())
  //   data-waypoint-count initial count shown before waypoints load (list mode)
  //
  // Public API
  //   configure({ apiFetch })  – inject apiFetch(method, path, body?) helper
  //   setRoute(route)      – set route object (detail mode)
  //   setWaypoints(waypoints)  – render the table
  //   getWaypoints()           – return current waypoint array
  //
  // Events emitted (all bubble)
  //   waypoints-changed  detail: { waypoints, routeId }
  //   save-error         detail: { message, routeId }
  //   add-stop           detail: { routeId }                    (list mode)
  //   edit-stop          detail: { routeId, waypointId }        (list mode)
  //   delete-stop        detail: { routeId, waypointId, name }  (list mode)

  class StopManifest extends HTMLElement {
    constructor() {
      super();
      this._waypoints = [];
      this._route   = null;
      this._routeId = null;
      this._apiFetch  = null;
      this._mode      = 'detail';
    }

    connectedCallback() {
      this._mode      = this.dataset.mode || 'detail';
      this._routeId = this.dataset.routeId || null;
      if (this._mode === 'list') {
        this._buildList();
      } else {
        this._buildDetail();
      }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    configure(options) {
      this._apiFetch = options.apiFetch || null;
    }

    setRoute(route) {
      this._route   = route;
      this._routeId = route ? route.id : null;
    }

    setWaypoints(waypoints) {
      this._waypoints = (waypoints || []).slice();
      this._renderTable();
      if (this._mode === 'list') {
        this._updateCount();
      }
    }

    getWaypoints() {
      return this._waypoints.slice();
    }

    // ── Private: DOM scaffolding ──────────────────────────────────────────────

    _buildDetail() {
      this.innerHTML =
        '<div class="card-header bg-transparent border-0 px-4 pt-4 pb-0">' +
          '<div class="d-flex align-items-start justify-content-between gap-3 flex-wrap">' +
            '<div>' +
              '<div class="page-kicker mb-2"><i class="bi bi-list-check"></i>Stop Manifest</div>' +
              '<h2 class="h5 fw-bold mb-1"><i class="bi bi-geo-alt me-2"></i>Stops</h2>' +
              '<p class="text-body-secondary small mb-0">Review and reorder pickup and delivery stops from the route manifest below.</p>' +
            '</div>' +
            '<span class="sm-reorder-hint small text-body-secondary">Drag stops to reorder</span>' +
          '</div>' +
        '</div>' +
        '<div class="card-body px-4 pb-4 pt-3">' +
          '<div class="sm-body"></div>' +
        '</div>';
    }

    _buildList() {
      var count = parseInt(this.dataset.waypointCount || '0', 10);
      this.innerHTML =
        '<div class="d-flex align-items-start justify-content-between gap-3 flex-wrap px-3 py-3 border-bottom">' +
          '<div>' +
            '<div class="page-kicker mb-2"><i class="bi bi-list-check"></i>Stop Manifest</div>' +
            '<h2 class="h5 fw-bold mb-1"><i class="bi bi-geo-alt me-2"></i>Stops</h2>' +
            '<p class="text-body-secondary small mb-0">Sort, review, and reorder pickup and delivery stops from the route manifest below.</p>' +
          '</div>' +
          '<div class="d-flex align-items-center gap-3 flex-wrap justify-content-end">' +
            '<span class="sm-count small text-body-secondary">' + escHtml(String(count)) + ' stops</span>' +
            '<button class="sm-add-stop btn btn-sm btn-outline-primary">' +
              '<i class="bi bi-geo-alt-fill me-1"></i>Add Stop' +
            '</button>' +
          '</div>' +
        '</div>' +
        '<div class="sm-body">' +
          '<div class="text-center py-4 text-body-secondary small">' +
            '<div class="spinner-border spinner-border-sm me-2" role="status">' +
              '<span class="visually-hidden">Loading…</span>' +
            '</div>Loading stops…' +
          '</div>' +
        '</div>';
      this._bindAddStop();
      this._bindRowActionsListener();
    }

    _bindAddStop() {
      var btn = this.querySelector('.sm-add-stop');
      if (!btn) return;
      var self = this;
      btn.addEventListener('click', function () {
        self.dispatchEvent(new CustomEvent('add-stop', {
          bubbles: true,
          detail: { routeId: self._routeId }
        }));
      });
    }

    // Bound once on sm-body (event delegation for edit/delete row buttons)
    _bindRowActionsListener() {
      var body = this.querySelector('.sm-body');
      if (!body) return;
      var self = this;
      body.addEventListener('click', function (e) {
        var editBtn = e.target.closest('.sm-edit-stop');
        if (editBtn) {
          self.dispatchEvent(new CustomEvent('edit-stop', {
            bubbles: true,
            detail: { routeId: self._routeId, waypointId: editBtn.dataset.wpId }
          }));
          return;
        }
        var delBtn = e.target.closest('.sm-delete-stop');
        if (delBtn) {
          self.dispatchEvent(new CustomEvent('delete-stop', {
            bubbles: true,
            detail: { routeId: self._routeId, waypointId: delBtn.dataset.wpId, name: delBtn.dataset.wpName }
          }));
        }
      });
    }

    // ── Private: rendering ────────────────────────────────────────────────────

    _updateCount() {
      var el = this.querySelector('.sm-count');
      if (!el) return;
      el.textContent = (this._waypoints ? this._waypoints.length : 0) + ' stops';
    }

    _renderTable() {
      var container = this.querySelector('.sm-body');
      if (!container) return;

      if (!this._waypoints || !this._waypoints.length) {
        container.innerHTML =
          '<div class="text-body-secondary text-center small py-4 mb-0">' +
            '<i class="bi bi-geo d-block fs-4 mb-2"></i>' +
            '<p class="mb-0">No stops for this delivery run.</p>' +
          '</div>';
        return;
      }

      var isList  = this._mode === 'list';
      var sorted  = (this._waypoints || []).slice().sort(function (a, b) { return (a.sequence || 0) - (b.sequence || 0); });
      var rows = sorted.map(function (w) {
        var safeId   = escHtml(w.id);
        var safeName = escHtml(w.name);
        var actionsCell = isList
          ? '<td class="text-end text-nowrap">' +
              '<button class="btn btn-sm btn-outline-secondary py-0 px-1 me-1 sm-edit-stop"' +
                ' title="Edit stop" data-wp-id="' + safeId + '" data-wp-name="' + safeName + '">' +
                '<i class="bi bi-pencil"></i>' +
              '</button>' +
              '<button class="btn btn-sm btn-outline-danger py-0 px-1 sm-delete-stop"' +
                ' title="Remove stop" data-wp-id="' + safeId + '" data-wp-name="' + safeName + '">' +
                '<i class="bi bi-trash3"></i>' +
              '</button>' +
            '</td>'
          : '';
        return (
          '<tr class="wp-row" data-wp-id="' + safeId + '" draggable="true">' +
            '<td class="wp-handle text-body-secondary text-center px-2"><i class="bi bi-grip-vertical"></i></td>' +
            '<td class="text-center fw-semibold text-body-secondary">' + escHtml(String(w.sequence)) + '</td>' +
            '<td>' + safeName + '</td>' +
            '<td class="font-monospace small">' + fmt(w.latitude)  + '</td>' +
            '<td class="font-monospace small">' + fmt(w.longitude) + '</td>' +
            '<td class="font-monospace small">' + fmt(w.altitude)  + ' m</td>' +
            '<td>' + (w.speed   != null ? fmt(w.speed)   + ' kt' : '<span class="text-body-secondary">—</span>') + '</td>' +
            '<td>' + (w.heading != null ? fmt(w.heading) + '°'   : '<span class="text-body-secondary">—</span>') + '</td>' +
            '<td>' + (w.holdTime ? w.holdTime + ' s'             : '<span class="text-body-secondary">—</span>') + '</td>' +
            actionsCell +
          '</tr>'
        );
      }).join('');

      container.innerHTML =
        '<div class="table-responsive">' +
          '<table class="table table-sm table-hover mb-0 align-middle small">' +
            '<thead class="table-light text-uppercase small">' +
              '<tr>' +
                '<th class="table-col-handle"></th>' +
                '<th class="table-col-sequence text-center">#</th>' +
                '<th>Stop</th>' +
                '<th>Latitude</th>' +
                '<th>Longitude</th>' +
                '<th>Cruise Altitude</th>' +
                '<th>Cruise Speed</th>' +
                '<th>' + (isList ? 'Approach' : 'Course') + '</th>' +
                '<th>Service</th>' +
                (isList ? '<th></th>' : '') +
              '</tr>' +
            '</thead>' +
            '<tbody>' + rows + '</tbody>' +
          '</table>' +
        '</div>';

      this._initDragAndDrop(container);
    }

    // ── Private: drag-and-drop ────────────────────────────────────────────────

    _initDragAndDrop(container) {
      var tbody = container.querySelector('tbody');
      if (!tbody) return;
      var self = this;
      var dragSource = null;

      Array.prototype.forEach.call(tbody.querySelectorAll('tr.wp-row'), function (row) {
        row.addEventListener('dragstart', function (e) {
          dragSource = row;
          row.classList.add('wp-dragging');
          e.dataTransfer.effectAllowed = 'move';
          e.dataTransfer.setData('text/plain', row.dataset.wpId);
        });

        row.addEventListener('dragend', function () {
          row.classList.remove('wp-dragging');
          Array.prototype.forEach.call(tbody.querySelectorAll('tr.wp-row'), function (r) {
            r.classList.remove('wp-drag-over');
          });
        });

        row.addEventListener('dragover', function (e) {
          e.preventDefault();
          e.dataTransfer.dropEffect = 'move';
          if (row === dragSource) return;
          Array.prototype.forEach.call(tbody.querySelectorAll('tr.wp-row'), function (r) {
            r.classList.remove('wp-drag-over');
          });
          row.classList.add('wp-drag-over');
        });

        row.addEventListener('dragleave', function () {
          row.classList.remove('wp-drag-over');
        });

        row.addEventListener('drop', function (e) {
          e.preventDefault();
          if (!dragSource || dragSource === row) return;
          var all = Array.prototype.slice.call(tbody.querySelectorAll('tr.wp-row'));
          if (all.indexOf(dragSource) < all.indexOf(row)) {
            tbody.insertBefore(dragSource, row.nextSibling);
          } else {
            tbody.insertBefore(dragSource, row);
          }
          row.classList.remove('wp-drag-over');
          self._saveReorder(tbody);
        });
      });
    }

    _saveReorder(tbody) {
      if (!this._routeId || !this._apiFetch) return;
      var self = this;
      var ids = Array.prototype.map.call(
        tbody.querySelectorAll('tr.wp-row'),
        function (r) { return r.dataset.wpId; }
      );
      this._apiFetch('PUT', '/routes/' + this._routeId + '/waypoints/reorder', { waypointIds: ids })
        .then(function (data) {
          if (data && data.waypoints) {
            self._waypoints = data.waypoints.slice();
            if (self._mode === 'list') {
              // Update sequence cells in-place to avoid resetting drag state
              var rows = tbody.querySelectorAll('tr.wp-row');
              data.waypoints.slice().sort(function (a, b) {
                return (a.sequence || 0) - (b.sequence || 0);
              }).forEach(function (w, i) {
                var seqCell = rows[i] && rows[i].cells[1];
                if (seqCell) seqCell.textContent = w.sequence;
              });
            }
          }
          self.dispatchEvent(new CustomEvent('waypoints-changed', {
            bubbles: true,
            detail: { waypoints: self._waypoints.slice(), routeId: self._routeId }
          }));
        })
        .catch(function (err) {
          if (self._mode === 'detail') self._renderTable();
          self.dispatchEvent(new CustomEvent('save-error', {
            bubbles: true,
            detail: { message: 'Failed to save stop order: ' + err.message, routeId: self._routeId }
          }));
        });
    }
  }

  customElements.define('stop-manifest', StopManifest);

})();
