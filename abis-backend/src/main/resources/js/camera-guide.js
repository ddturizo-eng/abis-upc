/**
 * camera-guide.js
 * Maneja el recuadro guía animado sobre el video de la cámara.
 * Sin dependencias externas — Canvas 2D API puro.
 */

class CameraGuide {
  /**
   * @param {HTMLVideoElement} videoEl  - Elemento video con el stream
   * @param {HTMLCanvasElement} canvasEl - Canvas superpuesto sobre el video
   */
  constructor(videoEl, canvasEl) {
    this.video = videoEl;
    this.canvas = canvasEl;
    this.ctx = canvasEl.getContext('2d');
    this._animFrameId = null;
    this._guideRect = null;

    this.COLOR_OVERLAY = 'rgba(0, 0, 0, 0.55)';
    this.COLOR_BORDER = '#00C896';
    this.COLOR_CORNER = '#00C896';
    this.COLOR_READY = '#00C896';
    this.COLOR_ALIGNING = '#FFFFFF';
  }

  /**
   * Calcula el rectángulo guía centrado con proporción ID-1 (1.586:1).
   */
  calcGuideRect() {
    const W = this.canvas.width;
    const H = this.canvas.height;
    const isMobile = W < 600;

    const occupancy = isMobile ? 0.92 : 0.88;
    const ID1_RATIO = 85.6 / 54.0;

    let guideW = W * occupancy;
    let guideH = guideW / ID1_RATIO;

    const maxHeight = H * 0.70;
    if (guideH > maxHeight) {
      guideH = maxHeight;
      guideW = guideH * ID1_RATIO;
    }

    const x = (W - guideW) / 2;
    const y = (H - guideH) / 2 - (isMobile ? 20 : 30);

    this._guideRect = {
      x: Math.round(x),
      y: Math.round(y),
      width: Math.round(guideW),
      height: Math.round(guideH),
    };

    return this._guideRect;
  }

  /**
   * Dibuja el overlay completo: zona oscura + recuadro claro + esquinas.
   * @param {boolean} isReady - True: esquinas verdes "listo", False: blancas "alineando"
   */
  drawOverlay(isReady = false) {
    const ctx = this.ctx;
    const W = this.canvas.width;
    const H = this.canvas.height;
    const r = this._guideRect;
    if (!r) return;

    ctx.clearRect(0, 0, W, H);

    ctx.fillStyle = this.COLOR_OVERLAY;
    ctx.fillRect(0, 0, W, H);

    ctx.clearRect(r.x, r.y, r.width, r.height);

    const borderColor = isReady ? this.COLOR_READY : 'rgba(255,255,255,0.6)';
    ctx.strokeStyle = borderColor;
    ctx.lineWidth = 2;
    ctx.strokeRect(r.x, r.y, r.width, r.height);

    const cornerLen = Math.min(r.width, r.height) * 0.12;
    const cornerWidth = 4;
    const cornerColor = isReady ? this.COLOR_CORNER : 'rgba(255,255,255,0.9)';

    ctx.strokeStyle = cornerColor;
    ctx.lineWidth = cornerWidth;
    ctx.lineCap = 'round';

    const corners = [
      [[r.x, r.y + cornerLen], [r.x, r.y], [r.x + cornerLen, r.y]],
      [[r.x + r.width - cornerLen, r.y], [r.x + r.width, r.y], [r.x + r.width, r.y + cornerLen]],
      [[r.x + r.width, r.y + r.height - cornerLen], [r.x + r.width, r.y + r.height], [r.x + r.width - cornerLen, r.y + r.height]],
      [[r.x + cornerLen, r.y + r.height], [r.x, r.y + r.height], [r.x, r.y + r.height - cornerLen]],
    ];

    corners.forEach(([start, mid, end]) => {
      ctx.beginPath();
      ctx.moveTo(...start);
      ctx.lineTo(...mid);
      ctx.lineTo(...end);
      ctx.stroke();
    });

    const statusY = r.y + r.height + 28;
    ctx.font = 'bold 14px system-ui, sans-serif';
    ctx.textAlign = 'center';

    if (isReady) {
      ctx.fillStyle = this.COLOR_READY;
      ctx.fillText('✓  LISTO PARA CAPTURAR', W / 2, statusY);
    } else {
      ctx.fillStyle = 'rgba(255,255,255,0.85)';
      ctx.fillText('Alineando documento...', W / 2, statusY);
    }

    ctx.font = '13px system-ui, sans-serif';
    ctx.fillStyle = 'rgba(255,255,255,0.75)';
    ctx.fillText('Coloque el documento dentro del recuadro', W / 2, r.y - 14);
  }

  syncCanvasSize() {
    const vw = this.video.videoWidth || this.video.clientWidth;
    const vh = this.video.videoHeight || this.video.clientHeight;
    if (vw > 0 && vh > 0) {
      if (this.canvas.width !== vw || this.canvas.height !== vh) {
        this.canvas.width = vw;
        this.canvas.height = vh;
        this.calcGuideRect();
      }
    }
  }

  startLoop() {
    const loop = () => {
      this.syncCanvasSize();
      if (this._guideRect) {
        this.drawOverlay(false);
      } else {
        this.calcGuideRect();
      }
      this._animFrameId = requestAnimationFrame(loop);
    };
    this._animFrameId = requestAnimationFrame(loop);
  }

  stopLoop() {
    if (this._animFrameId) {
      cancelAnimationFrame(this._animFrameId);
      this._animFrameId = null;
    }
  }

  showReadyFeedback() {
    return new Promise((resolve) => {
      this.stopLoop();
      this.drawOverlay(true);
      setTimeout(() => resolve(), 800);
    });
  }

  cropToGuide() {
    const r = this._guideRect;
    if (!r) return null;

    const scaleX = this.video.videoWidth / this.canvas.width;
    const scaleY = this.video.videoHeight / this.canvas.height;

    const srcX = r.x * scaleX;
    const srcY = r.y * scaleY;
    const srcW = r.width * scaleX;
    const srcH = r.height * scaleY;

    const outCanvas = document.createElement('canvas');
    outCanvas.width = 1200;
    outCanvas.height = Math.round(1200 / (85.6 / 54.0));

    const outCtx = outCanvas.getContext('2d');
    outCtx.drawImage(
      this.video,
      srcX, srcY, srcW, srcH,
      0, 0, outCanvas.width, outCanvas.height
    );

    return outCanvas;
  }
}

window.CameraGuide = CameraGuide;
