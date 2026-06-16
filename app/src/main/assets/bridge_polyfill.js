// Polyfill chrome.runtime.sendMessage to route extension messages to Android bridge
(function () {
  if (window.__bridgeInstalled) return;
  window.__bridgeInstalled = true;
  window.__bridgeCallbacks = {};

  // Fake chrome.runtime
  window.chrome = window.chrome || {};
  window.chrome.runtime = window.chrome.runtime || {};
  window.chrome.runtime.sendMessage = function (extensionIdOrMsg, msgOrCallback, maybeCallback) {
    var msg, callback;
    if (typeof extensionIdOrMsg === 'string') {
      msg = msgOrCallback;
      callback = maybeCallback;
    } else {
      msg = extensionIdOrMsg;
      callback = msgOrCallback;
    }
    var id = Math.random().toString(36).slice(2) + Date.now();
    if (typeof callback === 'function') {
      window.__bridgeCallbacks[id] = callback;
    }
    try {
      AndroidBridge.postMessage(JSON.stringify({ id: id, name: msg.name, body: msg.body || msg }));
    } catch (e) {
      console.warn('[Bridge] AndroidBridge not available', e);
      if (typeof callback === 'function') {
        callback({ success: false, error: 'Bridge not available' });
      }
    }
  };

  // The content script relay uses window.postMessage with { name, body, instanceId }
  // Intercept those and forward to Android, reply back as relayed messages
  window.addEventListener('message', function (event) {
    if (event.source !== window) return;
    var data = event.data;
    if (!data || !data.name || data.relayed || data.__internal) return;
    var knownMessages = ['hello', 'makeRequest', 'prepareStream', 'openPage'];
    if (knownMessages.indexOf(data.name) === -1) return;

    var id = data.instanceId || (Math.random().toString(36).slice(2) + Date.now());
    window.__bridgeCallbacks[id] = function (response) {
      window.postMessage({
        name: data.name,
        relayId: data.relayId,
        instanceId: data.instanceId,
        body: response,
        relayed: true
      }, '/');
    };
    try {
      AndroidBridge.postMessage(JSON.stringify({ id: id, name: data.name, body: data.body }));
    } catch (e) {
      console.warn('[Bridge] AndroidBridge not available for relay', e);
    }
  });

  // Called by Android to deliver responses
  window.__bridgeResolve = function (id, responseJson) {
    try {
      var response = JSON.parse(responseJson);
      var cb = window.__bridgeCallbacks[id];
      if (cb) {
        delete window.__bridgeCallbacks[id];
        cb(response);
      }
    } catch (e) {
      console.error('[Bridge] Failed to resolve callback', id, e);
    }
  };

  // Apply saved zoom level to page
  if (typeof AndroidZoom !== 'undefined') {
    var zoom = AndroidZoom.getZoom();
    document.documentElement.style.zoom = (zoom / 100).toFixed(2);

    function removeZoomSlider() {
      var old = document.getElementById('__zs_zoom');
      if (old) old.remove();
      var oldTab = document.getElementById('__zs_tab');
      if (oldTab) oldTab.remove();
    }

    function injectZoomSlider() {
      var path = window.location.pathname.replace(/\/$/, '');
      if (path !== '/settings') { removeZoomSlider(); return; }
      if (document.getElementById('__zs_zoom')) return;
      if (!document.body) { document.addEventListener('DOMContentLoaded', injectZoomSlider); return; }
      var z = AndroidZoom.getZoom();
      var hidden = AndroidZoom.isHidden();
      var el = document.createElement('div');
      el.id = '__zs_zoom';
      el.style.cssText = 'position:fixed;bottom:80px;left:50%;transform:translateX(-50%);z-index:99999;background:#1c1c2e;border:1px solid rgba(255,255,255,0.1);border-radius:16px;padding:12px 20px;display:flex;align-items:center;gap:12px;box-shadow:0 4px 24px rgba(0,0,0,0.6)';
      el.innerHTML =
        '<span style="color:#aaa;font-size:13px;white-space:nowrap">Page Zoom</span>' +
        '<input id="__zs_range" type="range" min="50" max="150" value="' + z + '" style="width:160px;accent-color:#a855f7">' +
        '<span id="__zs_label" style="color:#fff;font-size:13px;width:36px;text-align:right">' + z + '%</span>' +
        '<button id="__zs_hide" style="background:none;border:none;color:#666;font-size:16px;cursor:pointer;padding:0 0 0 4px;line-height:1">✕</button>';
      document.body.appendChild(el);

      // Tab/ribbon shown when pill is hidden
      var tab = document.createElement('div');
      tab.id = '__zs_tab';
      tab.textContent = 'Zoom';
      tab.style.cssText = 'display:none;position:fixed;bottom:120px;right:0;z-index:99999;background:#1c1c2e;border:1px solid rgba(255,255,255,0.1);border-right:none;border-radius:8px 0 0 8px;padding:8px 10px;color:#aaa;font-size:12px;cursor:pointer;writing-mode:vertical-rl;letter-spacing:1px;box-shadow:-2px 2px 8px rgba(0,0,0,0.4)';
      document.body.appendChild(tab);

      document.getElementById('__zs_hide').addEventListener('click', function() {
        el.style.display = 'none';
        tab.style.display = 'block';
        AndroidZoom.setHidden(true);
      });
      tab.addEventListener('click', function() {
        tab.style.display = 'none';
        el.style.display = 'flex';
        AndroidZoom.setHidden(false);
      });

      if (hidden) {
        el.style.display = 'none';
        tab.style.display = 'block';
      }

      document.getElementById('__zs_range').addEventListener('input', function() {
        var v = parseInt(this.value);
        document.documentElement.style.zoom = (v / 100).toFixed(2);
        document.getElementById('__zs_label').textContent = v + '%';
        AndroidZoom.setZoom(v);
      });
    }

    // SPA navigation — intercept history.pushState/replaceState
    var _push = history.pushState.bind(history);
    var _replace = history.replaceState.bind(history);
    history.pushState = function() { _push.apply(history, arguments); injectZoomSlider(); };
    history.replaceState = function() { _replace.apply(history, arguments); injectZoomSlider(); };
    window.addEventListener('popstate', injectZoomSlider);

    if (document.body) injectZoomSlider();
    else document.addEventListener('DOMContentLoaded', injectZoomSlider);
  }

  // Observe DOM so it catches dynamically added videos too
  function patchVideos() {
    document.querySelectorAll('video[poster]').forEach(function(v) {
      v.removeAttribute('poster');
      v.style.background = '#000';
    });
  }
  patchVideos();
  new MutationObserver(patchVideos).observe(document.documentElement, { childList: true, subtree: true });

  // Intercept video.requestPictureInPicture() and route to Android native PiP
  var _origPip = HTMLVideoElement.prototype.requestPictureInPicture;
  HTMLVideoElement.prototype.requestPictureInPicture = function () {
    try {
      if (typeof AndroidPip !== 'undefined') {
        AndroidPip.enter(this.videoWidth || 16, this.videoHeight || 9);
        return Promise.resolve();
      }
    } catch (e) {}
    if (_origPip) return _origPip.call(this);
    return Promise.reject(new Error('PiP not supported'));
  };

  // Poll video state every second and update Android MediaSession (lock screen controls)
  // Also toggle swipe-to-refresh based on whether a video is playing
  if (typeof AndroidMedia !== 'undefined') {
    setInterval(function() {
      var v = document.querySelector('video');
      if (!v) return;
      try {
        var playing = !v.paused && !v.ended;
        var meta = navigator.mediaSession && navigator.mediaSession.metadata;
        var title = (meta && meta.title) ? meta.title : (document.title || '');
        AndroidMedia.updateState(
          playing,
          title,
          Math.round((v.duration || 0) * 1000),
          Math.round((v.currentTime || 0) * 1000)
        );
        AndroidMedia.setSwipeRefresh(!playing);
      } catch(e) {}
    }, 1000);
  }
})();
