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
  if (typeof AndroidMedia !== 'undefined') {
    setInterval(function() {
      var v = document.querySelector('video');
      if (!v) return;
      try {
        AndroidMedia.updateState(
          !v.paused && !v.ended,
          document.title || '',
          Math.round((v.duration || 0) * 1000),
          Math.round((v.currentTime || 0) * 1000)
        );
      } catch(e) {}
    }, 1000);
  }
})();
