(function (global) {
  'use strict';

  var ROOT = document.documentElement;
  var DEFAULT_THEME = 'light';
  var DEFAULT_ICON_ID = 'theme-icon';

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function getStoredTheme() {
    return localStorage.getItem('bs-theme') || ROOT.getAttribute('data-bs-theme') || DEFAULT_THEME;
  }

  function updateThemeIcon(theme, iconId) {
    var icon = document.getElementById(iconId || DEFAULT_ICON_ID);
    if (!icon) return;
    icon.className = theme === 'dark' ? 'bi bi-moon-stars-fill' : 'bi bi-sun-fill';
  }

  function applyTheme(theme, iconId) {
    var nextTheme = theme || DEFAULT_THEME;
    ROOT.setAttribute('data-bs-theme', nextTheme);
    updateThemeIcon(nextTheme, iconId);
    localStorage.setItem('bs-theme', nextTheme);
    return nextTheme;
  }

  function initializeTheme(iconId) {
    return applyTheme(getStoredTheme(), iconId || DEFAULT_ICON_ID);
  }

  function toggleTheme(iconId) {
    var nextTheme = ROOT.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
    return applyTheme(nextTheme, iconId || DEFAULT_ICON_ID);
  }

  function ensureToastContainer(containerId) {
    var resolvedId = containerId || 'alert-area';
    var container = document.getElementById(resolvedId);
    if (container) return container;

    container = document.createElement('div');
    container.id = resolvedId;
    container.className = 'toast-container app-toast-stack position-fixed bottom-0 end-0 p-3';
    document.body.appendChild(container);
    return container;
  }

  function toastThemeClass(type) {
    switch (type) {
      case 'success':
        return 'text-bg-success';
      case 'warning':
        return 'text-bg-warning';
      case 'primary':
        return 'text-bg-primary';
      case 'secondary':
        return 'text-bg-secondary';
      default:
        return 'text-bg-danger';
    }
  }

  function showAppToast(options) {
    var settings = options || {};
    var type = settings.type || 'danger';
    var container = ensureToastContainer(settings.containerId);
    var message = settings.html ? String(settings.message || '') : escapeHtml(settings.message || '');
    var closeClass = type === 'warning' ? 'btn-close' : 'btn-close btn-close-white';
    var toast = document.createElement('div');

    toast.className = 'toast align-items-center border-0 ' + toastThemeClass(type);
    toast.setAttribute('role', 'alert');
    toast.setAttribute('aria-live', 'assertive');
    toast.setAttribute('aria-atomic', 'true');
    toast.innerHTML =
      '<div class="d-flex">' +
        '<div class="toast-body">' + message + '</div>' +
        '<button type="button" class="' + closeClass + ' me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>' +
      '</div>';

    container.appendChild(toast);
    toast.addEventListener('hidden.bs.toast', function () {
      toast.remove();
    });

    bootstrap.Toast.getOrCreateInstance(toast, {
      delay: settings.delay || 3500
    }).show();

    return toast;
  }

  global.escapeHtml = escapeHtml;
  global.initializeTheme = initializeTheme;
  global.applyTheme = applyTheme;
  global.toggleTheme = function () {
    return toggleTheme(DEFAULT_ICON_ID);
  };
  global.showAppToast = showAppToast;

  initializeTheme(DEFAULT_ICON_ID);
  document.addEventListener('DOMContentLoaded', function () {
    initializeTheme(DEFAULT_ICON_ID);
  });
})(window);
