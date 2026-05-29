var FaceCapture = {
  STATE_IDLE: 'idle',
  STATE_LOADING: 'loading',
  STATE_DETECTING: 'detecting',
  STATE_CAPTURED: 'captured',
  STATE_ERROR: 'error',

  state: 'idle',
  isInitialized: false,
  isDetecting: false,
  isCapturing: false,
  isDestroying: false,
  isOpeningCamera: false,
  previewUrl: null,
  detectionRafId: null,
  lastDetectionTime: 0,
  tabVisible: true,

  camera: null,
  overlay: null,

  DETECTION_INTERVAL_MS: 66,

  init: function () {
    if (FaceCapture.isInitialized) {
      console.warn('FaceCapture: ya inicializado, ignorando.');
      return Promise.resolve();
    }
    if (FaceCapture.isDestroying) {
      return Promise.resolve();
    }

    var data = VotanteSession.getDatosRegistro();
    var id = VotanteSession.getIdentificacion();
    if (!data || !id) {
      Router.irA(1);
      return Promise.resolve();
    }

    FaceCapture.isInitialized = true;
    FaceCapture.isDetecting = false;
    FaceCapture.isCapturing = false;
    FaceCapture.isOpeningCamera = false;
    FaceCapture.tabVisible = true;
    FaceCapture.state = FaceCapture.STATE_IDLE;

    FaceCapture.camera = new CameraManager();
    FaceCapture.overlay = new OverlayRenderer();
    FaceCapture.overlay.init('face-overlay', 640, 480);

    var voterName = [data.primerNombre, data.segundoNombre, data.primerApellido, data.segundoApellido]
      .filter(Boolean).join(' ');
    var voterIdEl = document.getElementById('bio-voter-id');
    var voterNameEl = document.getElementById('bio-voter-name');
    if (voterNameEl) voterNameEl.textContent = voterName;
    if (voterIdEl) voterIdEl.textContent = 'ID: ' + id;

    wireButtons();
    FaceCapture._bindEvents();

    return FaceCapture.loadModels();
  },

  _bindEvents: function () {
    FaceCapture._onVisibilityChange = function () {
      if (document.hidden) {
        FaceCapture.tabVisible = false;
        if (FaceCapture.isDetecting) FaceCapture.stopDetectionLoop();
      }
    };

    FaceCapture._onVisibilityRestore = function () {
      if (!document.hidden && !FaceCapture.tabVisible) {
        FaceCapture.tabVisible = true;
        if (FaceCapture.isInitialized && FaceCapture.camera && FaceCapture.camera.isActive()
            && FaceCapture.state !== FaceCapture.STATE_CAPTURED) {
          FaceCapture.startDetectionLoop();
        }
      }
    };

    FaceCapture._onBeforeUnload = function () {
      FaceCapture.stopDetectionLoop();
      if (FaceCapture.camera) FaceCapture.camera.stop();
      if (FaceCapture.previewUrl) { URL.revokeObjectURL(FaceCapture.previewUrl); FaceCapture.previewUrl = null; }
    };

    document.addEventListener('visibilitychange', FaceCapture._onVisibilityChange);
    document.addEventListener('visibilitychange', FaceCapture._onVisibilityRestore);
    window.addEventListener('beforeunload', FaceCapture._onBeforeUnload);
  },

  _unbindEvents: function () {
    if (FaceCapture._onVisibilityChange) document.removeEventListener('visibilitychange', FaceCapture._onVisibilityChange);
    if (FaceCapture._onVisibilityRestore) document.removeEventListener('visibilitychange', FaceCapture._onVisibilityRestore);
    if (FaceCapture._onBeforeUnload) window.removeEventListener('beforeunload', FaceCapture._onBeforeUnload);
    FaceCapture._onVisibilityChange = null;
    FaceCapture._onVisibilityRestore = null;
    FaceCapture._onBeforeUnload = null;
  },

  loadModels: function () {
    FaceCapture.state = FaceCapture.STATE_LOADING;
    FaceCapture.updateStatus('Cargando detector facial...');

    return FaceDetectModule.initialize()
      .then(function () {
        FaceCapture.state = FaceCapture.STATE_IDLE;
        FaceCapture.updateStatus('Listo. Abra la camara.');
        enableButton('btn-abrir-camara', true);
      })
      .catch(function (err) {
        FaceCapture.state = FaceCapture.STATE_ERROR;
        FaceCapture.updateStatus('Error: ' + String(err));
        if (window.showToast) window.showToast('No se pudo cargar el detector facial.', 'error');
      });
  },

  openCamera: function () {
    if (FaceCapture.isDestroying || FaceCapture.isOpeningCamera) return;

    var video = document.getElementById('foto-video');
    if (!video) return;

    if (!FaceDetectModule.isReady()) {
      FaceCapture.showError('El detector aun no esta listo.');
      return;
    }

    FaceCapture.isOpeningCamera = true;
    FaceCapture.updateStatus('Abriendo camara...');
    enableButton('btn-abrir-camara', false);

    FaceCapture.camera.open(video, { width: 640, height: 480, facingMode: 'user' })
      .then(function () {
        FaceCapture.isOpeningCamera = false;
        if (FaceCapture.isDestroying) return;

        video.classList.remove('hidden');
        hideEl('foto-placeholder');
        hideEl('foto-preview');
        showEl('foto-guide');
        showEl('face-overlay');
        hideEl('btn-abrir-camara');

    var btnCapturar = document.getElementById('btn-capturar-foto');
    if (btnCapturar) {
      btnCapturar.classList.remove('hidden');
      btnCapturar.disabled = false;
    }
    hideEl('btn-repetir-foto');

        FaceCapture.overlay.resize(video.videoWidth || 640, video.videoHeight || 480);
        FaceCapture.state = FaceCapture.STATE_DETECTING;
        FaceCapture.updateStatus('Camara activa. Capture cuando este listo.');
        FaceCapture.startDetectionLoop();
      })
      .catch(function (err) {
        FaceCapture.isOpeningCamera = false;
        if (FaceCapture.isDestroying) return;
        FaceCapture.state = FaceCapture.STATE_ERROR;
        FaceCapture.updateStatus('Error: ' + String(err));
        FaceCapture.showError('No se pudo acceder a la camara.');
        enableButton('btn-abrir-camara', true);
      });
  },

  startDetectionLoop: function () {
    if (FaceCapture.isDetecting) return;
    FaceCapture.isDetecting = true;
    FaceCapture.lastDetectionTime = 0;
    FaceCapture.detectionRafId = requestAnimationFrame(function (ts) { detectionLoop(ts); });
  },

  stopDetectionLoop: function () {
    FaceCapture.isDetecting = false;
    if (FaceCapture.detectionRafId) {
      cancelAnimationFrame(FaceCapture.detectionRafId);
      FaceCapture.detectionRafId = null;
    }
  },

  capture: function () {
    if (FaceCapture.isCapturing) return;

    var video = FaceCapture.camera.getVideo();
    if (!video || !video.videoWidth) {
      FaceCapture.showError('La camara no esta disponible.');
      return;
    }

    FaceCapture.isCapturing = true;
    FaceCapture.updateStatus('Capturando...');

    var canvas = document.getElementById('foto-canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext('2d').drawImage(video, 0, 0);

    canvas.toBlob(function (blob) {
      FaceCapture.isCapturing = false;
      if (FaceCapture.isDestroying || !blob) {
        if (!blob) FaceCapture.showError('No se pudo capturar la imagen.');
        return;
      }

      FaceCapture.stopDetectionLoop();
      FaceCapture.camera.stop();

      if (FaceCapture.previewUrl) URL.revokeObjectURL(FaceCapture.previewUrl);
      FaceCapture.previewUrl = URL.createObjectURL(blob);

      showEl('foto-preview');
      var previewImg = document.getElementById('foto-preview');
      if (previewImg) previewImg.src = FaceCapture.previewUrl;

      hideEl('foto-video');
      hideEl('foto-guide');
      hideEl('face-overlay');
      if (FaceCapture.overlay) FaceCapture.overlay.clear();
      hideEl('btn-capturar-foto');
      showEl('btn-repetir-foto');
      showEl('foto-review');
      hideEl('foto-required-warning');

      var btnHuella = document.getElementById('btn-ir-huella');
      if (btnHuella) {
        btnHuella.disabled = false;
      }

      FaceCapture.state = FaceCapture.STATE_CAPTURED;
      FaceCapture.updateStatus('Procesando imagen...');

      Router.paso2Completo = true;

      if (typeof actualizarEstadoBiometria === 'function') {
        actualizarEstadoBiometria('completado', 'estado-foto-capturada');
      }

      var datos = VotanteSession.getDatosRegistro();
      if (datos) {
        datos.fotoPreview = FaceCapture.previewUrl;
        VotanteSession.setDatosRegistro(datos);
      }

      FaceCapture.processAndSave(blob);
    }, 'image/jpeg', 0.92);
  },

  processAndSave: function (blob) {
    var identificacion = VotanteSession.getIdentificacion();

    FaceCapture.updateStatus('Recortando rostro...');

    CaptureFlow.executeWithFallback(blob, identificacion)
      .then(function (result) {
        if (FaceCapture.isDestroying) return;
        FaceCapture.updateStatus('Foto guardada correctamente.');
        if (result && result.foto_url) {
          var datos = VotanteSession.getDatosRegistro();
          if (datos) { datos.fotoUrl = result.foto_url; VotanteSession.setDatosRegistro(datos); }
        }
        if (typeof actualizarEstadoBiometria === 'function') {
          actualizarEstadoBiometria('completado', 'estado-foto-guardada');
        }
      })
      .catch(function (err) {
        if (FaceCapture.isDestroying) return;
        console.error('FaceCapture: error al procesar:', String(err));
        FaceCapture.showError('La imagen se guardo sin recortar. Reintente si lo desea.');
      });
  },

  repeat: function () {
    if (FaceCapture.isDestroying) return;

    FaceCapture.state = FaceCapture.STATE_IDLE;
    FaceCapture.previewUrl = null;

    hideEl('foto-preview');
    var previewImg = document.getElementById('foto-preview');
    if (previewImg) previewImg.src = '';

    showEl('face-overlay');
    hideEl('foto-review');
    showEl('foto-required-warning');

    var btnHuella = document.getElementById('btn-ir-huella');
    if (btnHuella) {
      btnHuella.disabled = true;
    }

    FaceCapture.openCamera();
  },

  destroy: function () {
    if (!FaceCapture.isInitialized) return;
    FaceCapture.isDestroying = true;

    FaceCapture.stopDetectionLoop();
    if (FaceCapture.camera) { FaceCapture.camera.stop(); FaceCapture.camera = null; }
    if (FaceCapture.overlay) { FaceCapture.overlay.clear(); FaceCapture.overlay = null; }
    FaceCapture._unbindEvents();

    FaceCapture.isInitialized = false;
    FaceCapture.isDetecting = false;
    FaceCapture.isCapturing = false;
    FaceCapture.isOpeningCamera = false;
    FaceCapture.state = FaceCapture.STATE_IDLE;
    FaceCapture.isDestroying = false;
  },

  updateStatus: function (msg) {
    var statusEl = document.getElementById('bio-status');
    if (statusEl) statusEl.textContent = msg;

    var dotEl = document.getElementById('bio-status-dot');
    if (!dotEl) return;

    if (FaceCapture.state === FaceCapture.STATE_CAPTURED || FaceCapture.state === FaceCapture.STATE_ERROR) {
      dotEl.style.display = 'none';
    } else {
      dotEl.style.display = '';
    }
  },

  showError: function (msg) {
    if (window.showToast) window.showToast(msg, 'error');
  }
};

function detectionLoop(timestamp) {
  if (!FaceCapture.isDetecting) return;
  if (FaceCapture.isDestroying) return;

  FaceCapture.detectionRafId = requestAnimationFrame(function (ts) { detectionLoop(ts); });

  if (timestamp - FaceCapture.lastDetectionTime < FaceCapture.DETECTION_INTERVAL_MS) return;
  FaceCapture.lastDetectionTime = timestamp;

  var video = FaceCapture.camera.getVideo();
  if (!video || video.readyState < 2 || video.videoWidth === 0) return;

  try {
    var results = FaceDetectModule.detectForVideo(video, timestamp);
    FaceCapture.overlay.draw(results.detections, 'detecting');
  } catch (err) {
  }
}

function showEl(id) {
  var el = document.getElementById(id);
  if (el) el.classList.remove('hidden');
}

function hideEl(id) {
  var el = document.getElementById(id);
  if (el) el.classList.add('hidden');
}

function enableButton(id, enabled) {
  var btn = document.getElementById(id);
  if (!btn) return;
  btn.disabled = !enabled;
}

function wireButtons() {
  var ids = ['btn-abrir-camara', 'btn-capturar-foto', 'btn-repetir-foto'];
  var fns = [
    function () { FaceCapture.openCamera(); },
    function () { FaceCapture.capture(); },
    function () { FaceCapture.repeat(); }
  ];

  ids.forEach(function (id, i) {
    var btn = document.getElementById(id);
    if (!btn) return;
    btn.replaceWith(btn.cloneNode(true));
    btn = document.getElementById(id);
    btn.addEventListener('click', fns[i]);
  });
}

window.FaceCapture = FaceCapture;
