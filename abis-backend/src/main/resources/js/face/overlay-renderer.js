var OverlayRenderer = function () {
  var canvas = null;
  var ctx = null;

  function init(canvasId, width, height) {
    canvas = document.getElementById(canvasId);
    if (!canvas) {
      console.warn('OverlayRenderer: canvas no encontrado:', canvasId);
      return false;
    }
    canvas.width = width || 640;
    canvas.height = height || 480;
    ctx = canvas.getContext('2d');
    return true;
  }

  function drawSafeZone() {
    if (!ctx) return;
    var w = canvas.width;
    var h = canvas.height;
    var cx = w / 2;
    var cy = h / 2;
    var rx = w * 0.325;
    var ry = h * 0.325;

    ctx.save();
    ctx.strokeStyle = 'rgba(76, 175, 80, 0.3)';
    ctx.lineWidth = 2;
    ctx.setLineDash([8, 4]);
    ctx.beginPath();
    ctx.ellipse(cx, cy, rx, ry, 0, 0, 2 * Math.PI);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.restore();
  }

  function draw(detections, validationState) {
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    drawSafeZone();

    if (!detections || detections.length === 0) return;

    var det = detections[0];
    var box = det.boundingBox;
    if (!box) return;

    var isValid = validationState === 'ready' || validationState === 'validating';
    var color = isValid ? '#00C896' : '#F59E0B';

    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 3;
    ctx.shadowColor = color;
    ctx.shadowBlur = isValid ? 6 : 0;
    ctx.strokeRect(box.originX, box.originY, box.width, box.height);
    ctx.restore();
  }

  function clear() {
    if (!ctx) return;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
  }

  function resize(w, h) {
    if (canvas) {
      canvas.width = w;
      canvas.height = h;
    }
  }

  return {
    init: init,
    draw: draw,
    clear: clear,
    resize: resize
  };
};

window.OverlayRenderer = OverlayRenderer;
