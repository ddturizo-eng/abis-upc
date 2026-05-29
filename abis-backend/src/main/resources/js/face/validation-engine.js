var ValidationEngine = function () {
  var state = 'no-face';
  var buffer = [];
  var MIN_SIZE = 140;
  var CENTER_TOLERANCE = 0.35;
  var STABILITY_MS = 1000;

  function evaluate(detections, videoWidth, videoHeight) {
    if (!videoWidth || !videoHeight) {
      setState('no-face');
      return;
    }

    if (!detections || detections.length === 0) {
      setState('no-face');
      return;
    }

    if (detections.length > 1) {
      setState('multiple');
      return;
    }

    var det = detections[0];
    var box = det.boundingBox;
    if (!box) {
      setState('no-face');
      return;
    }

    var w = box.width;
    var h = box.height;

    if (w < MIN_SIZE || h < MIN_SIZE) {
      setState('too-small');
      return;
    }

    var cx = box.originX + w / 2;
    var cy = box.originY + h / 2;
    var relX = Math.abs(cx - videoWidth / 2) / (videoWidth / 2);
    var relY = Math.abs(cy - videoHeight / 2) / (videoHeight / 2);

    if (relX > CENTER_TOLERANCE || relY > CENTER_TOLERANCE) {
      setState('off-center');
      return;
    }

    var now = performance.now();
    buffer.push({ timestamp: now });

    while (buffer.length > 0 && now - buffer[0].timestamp > STABILITY_MS) {
      buffer.shift();
    }

    if (buffer.length >= 5 && (now - buffer[0].timestamp) >= STABILITY_MS) {
      setState('ready');
    } else {
      setState('validating');
    }
  }

  function setState(newState) {
    if (newState !== 'validating' && newState !== 'ready') {
      buffer = [];
    }
    state = newState;
  }

  function getState() {
    return state;
  }

  function reset() {
    state = 'no-face';
    buffer = [];
  }

  function setStabilityMs(ms) {
    STABILITY_MS = ms;
  }

  return {
    evaluate: evaluate,
    getState: getState,
    reset: reset,
    setStabilityMs: setStabilityMs
  };
};

window.ValidationEngine = ValidationEngine;
