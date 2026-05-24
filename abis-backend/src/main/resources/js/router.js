const Router = {
  pasoActual: 0,
  consentimientoCompleto: false,
  paso2Completo: false,
  paso3Completo: false,
  paso4Confirmado: false,
  fragmentos: {
    0: 'consent-modal.html',
    1: 'paso1-documento.html',
    2: 'paso2-rostro.html',
    3: 'fingerprint-step.html',
    4: 'paso3-verificacion.html',
    5: 'paso4-completado.html'
  },
  async irA(n) {
    let destino = n;

    if (destino > 0 && !VotanteSession.getConsentimiento()) {
      destino = 0;
    }

    if (destino > 1 && !VotanteSession.getIdentificacion()) {
      destino = 1;
    }

    if (destino > 2 && !Router.paso2Completo) {
      destino = 2;
    }

    if (destino > 3 && !Router.paso3Completo) {
      destino = 3;
    }

    if (destino === 5 && !Router.paso4Confirmado) {
      destino = 4;
    }

    const response = await fetch(Router.fragmentos[destino], { cache: 'no-store' });
    if (!response.ok) {
      throw new Error('No se pudo cargar el paso ' + destino);
    }

    const html = await response.text();
    document.getElementById('step-content').innerHTML = html;
    Router.pasoActual = destino;
    Router.actualizarSidebar(destino);

    const inits = {
      0: () => ConsentStep.init(),
      1: initPaso1,
      2: () => FaceCapture.init(),
      3: () => FingerprintUI.init(),
      4: initPaso3,
      5: initPaso4
    };
    if (typeof inits[destino] === 'function') {
      inits[destino]();
    }
  },
  actualizarSidebar(pasoActivo) {
    document.querySelectorAll('[data-step]').forEach((item) => {
      const step = parseInt(item.dataset.step, 10);
      let state = 'pending';

      if (step < pasoActivo) {
        state = 'completed';
      } else if (step === pasoActivo) {
        state = 'active';
      } else if (
        (step === 1 && !VotanteSession.getConsentimiento()) ||
        (step > 1 && !VotanteSession.getIdentificacion()) ||
        (step > 2 && !Router.paso2Completo) ||
        (step > 3 && !Router.paso3Completo)
      ) {
        state = 'locked';
      }

      item.classList.remove('step-active', 'step-completed', 'step-pending', 'step-locked');
      item.classList.add('step-' + state);

      const indicator = item.querySelector('[data-step-indicator]');
      const title = item.querySelector('[data-step-title]');
      const label = item.querySelector('[data-step-status]');

      if (state === 'completed') {
        indicator.className = 'w-8 h-8 shrink-0 rounded-full bg-[#4CAF7D] text-white flex items-center justify-center text-[11px] font-bold';
        indicator.innerHTML = '<span class="material-symbols-outlined text-[14px]">check</span>';
        title.className = 'text-[13px] font-bold text-[#4CAF7D]';
        label.className = 'text-[10px] text-[#4CAF7D] font-bold uppercase tracking-widest';
        label.textContent = 'COMPLETADO';
      } else if (state === 'active') {
        indicator.className = 'w-8 h-8 shrink-0 rounded-full bg-[#004D33] text-white flex items-center justify-center text-[11px] font-bold';
        indicator.textContent = String(step);
        title.className = 'text-[13px] font-bold text-[#004D33]';
        label.className = 'text-[10px] text-[#004D33] font-bold uppercase tracking-widest';
        label.textContent = 'EN CURSO';
      } else if (state === 'locked') {
        indicator.className = 'w-8 h-8 shrink-0 rounded-full border border-slate-200 bg-[#F2F3F5] text-slate-400 flex items-center justify-center text-[11px] font-bold';
        indicator.innerHTML = '<span class="material-symbols-outlined text-[16px]">lock_outline</span>';
        title.className = 'text-[13px] font-medium text-slate-600';
        label.className = 'text-[10px] text-slate-400 font-bold uppercase tracking-widest';
        label.textContent = 'BLOQUEADO';
      } else {
        indicator.className = 'w-8 h-8 shrink-0 rounded-full border border-slate-200 bg-[#F2F3F5] text-slate-400 flex items-center justify-center text-[11px] font-bold';
        indicator.textContent = String(step);
        title.className = 'text-[13px] font-medium text-slate-600';
        label.className = 'text-[10px] text-slate-400 font-bold uppercase tracking-widest';
        label.textContent = 'PENDIENTE';
      }
    });
  }
};

const Paso1State = {
  currentDocType: 'CC',
  currentFile: null,
  cameraStream: null
};

let _cameraGuide = null;

// ── Estado IP Webcam ──────────────────────────────────────────────────────
let _ipWebcamInterval = null;   // Intervalo de polling de frames
let _ipWebcamConnected = false; // Estado de conexión
let _ipWebcamGuide = null;      // Instancia CameraGuide para el overlay
const IP_WEBCAM_FPS = 10;       // Frames por segundo del polling (10 = fluido sin saturar)

function actualizarEstadoOCR(estado, mensaje) {
  const statusBar = document.getElementById('ocr-status-bar');
  const statusIcon = document.getElementById('ocr-status-icon');
  const statusText = document.getElementById('ocr-status-text');
  const statusBadge = document.getElementById('ocr-status-badge');
  const ocrDataBadge = document.getElementById('ocr-data-badge');

  if (!statusBar) return;

  statusBar.classList.remove('bg-slate-50', 'bg-amber-50', 'bg-green-50', 'border-slate-200', 'border-amber-200', 'border-green-200');

  const estados = {
    'esperando': {
      icon: 'document_scanner',
      text: 'Esperando documento...',
      badge: 'OCR INACTIVO',
      bgClass: 'bg-slate-50',
      borderClass: 'border-slate-200'
    },
    'procesando': {
      icon: 'hourglass_empty',
      text: 'Procesando OCR...',
      badge: 'PROCESANDO',
      bgClass: 'bg-amber-50',
      borderClass: 'border-amber-200'
    },
    'detectado': {
      icon: 'description',
      text: 'Documento detectado',
      badge: 'DOCUMENTO DETECTADO',
      bgClass: 'bg-amber-50',
      borderClass: 'border-amber-200'
    },
    'completo': {
      icon: 'check_circle',
      text: mensaje || 'Datos extraídos correctamente',
      badge: 'DATOS EXTRAÍDOS',
      bgClass: 'bg-green-50',
      borderClass: 'border-green-200'
    },
    'error': {
      icon: 'error',
      text: mensaje || 'Error al procesar documento',
      badge: 'ERROR',
      bgClass: 'bg-red-50',
      borderClass: 'border-red-200'
    }
  };

  const config = estados[estado] || estados['esperando'];
  statusBar.classList.add(config.bgClass, config.borderClass);
  statusIcon.textContent = config.icon;
  statusIcon.className = 'material-symbols-outlined text-[20px] ' + (estado === 'error' ? 'text-red-500' : estado === 'completo' ? 'text-[#4CAF7D]' : 'text-amber-500');
  statusText.textContent = config.text;
  statusText.className = 'text-sm font-medium ' + (estado === 'error' ? 'text-red-600' : estado === 'completo' ? 'text-[#4CAF7D]' : 'text-slate-600');
  statusBadge.textContent = config.badge;
  statusBadge.className = 'text-[10px] font-bold px-2 py-1 rounded ' + (estado === 'error' ? 'bg-red-100 text-red-700' : estado === 'completo' ? 'bg-[#4CAF7D] text-white' : estado === 'procesando' ? 'bg-amber-100 text-amber-700' : 'bg-slate-200 text-slate-500') + ' uppercase tracking-wider';

  if (estado === 'completo' && ocrDataBadge) {
    ocrDataBadge.classList.remove('hidden');
  } else if (ocrDataBadge) {
    ocrDataBadge.classList.add('hidden');
  }
}

function actualizarEstadoBiometria(estado, idElemento) {
  var el = document.getElementById(idElemento);
  if (!el) return;
  var span = el.querySelector('span');
  if (!span) return;
  
  if (estado === 'completado') {
    el.className = 'flex items-center gap-2 p-2 rounded-lg border border-green-200 bg-green-50';
    span.className = 'w-6 h-6 rounded-full bg-[#4CAF7D] text-white flex items-center justify-center text-xs';
    span.textContent = '✓';
  } else if (estado === 'actual') {
    el.className = 'flex items-center gap-2 p-2 rounded-lg border border-amber-200 bg-amber-50';
    span.className = 'w-6 h-6 rounded-full bg-amber-400 text-white flex items-center justify-center text-xs animate-pulse';
    span.textContent = '●';
  } else if (estado === 'pendiente') {
    el.className = 'flex items-center gap-2 p-2 rounded-lg border border-slate-200 bg-slate-50';
    span.className = 'w-6 h-6 rounded-full bg-slate-300 text-white flex items-center justify-center text-xs';
span.textContent = '○';
  }
}

function mapDocType(type) {
  if (type === 'CE') return 'carnet_estudiantil';
  if (type === 'TI') return 'tarjeta_identidad';
  return 'auto';
}

function showPaso1Error(message) {
  const errorBox = document.getElementById('error-msg');
  if (!errorBox) return;
  errorBox.textContent = message;
  errorBox.classList.remove('hidden');
}

function hidePaso1Error() {
  const errorBox = document.getElementById('error-msg');
  if (errorBox) {
    errorBox.classList.add('hidden');
    errorBox.textContent = '';
  }
}

function fillFormFromOCR(data) {
  if (!(data.status === 'complete' || (data.overall_confidence && data.overall_confidence > 0))) {
    return;
  }

  const fields = {
    'f-primernombre': data.primer_nombre,
    'f-segundonombre': data.segundo_nombre,
    'f-primerapellido': data.primer_apellido,
    'f-segundoapellido': data.segundo_apellido,
    'f-identificacion': data.numero,
    'f-fecha-nacimiento': data.fecha_nacimiento
  };

  Object.entries(fields).forEach(([id, value]) => {
    const input = document.getElementById(id);
    if (input && value) {
      input.value = value;
      input.classList.add('filled');
    }
  });
}

function resetForm() {
  document.querySelectorAll('.form-field').forEach((field) => {
    if (field.tagName === 'SELECT') {
      field.selectedIndex = 0;
    } else {
      field.value = '';
    }
    field.classList.remove('filled');
  });

  Paso1State.currentFile = null;
  hidePaso1Error();
  actualizarEstadoOCR('esperando', 'Esperando documento...');

  const preview = document.getElementById('preview');
  const captureContent = document.getElementById('capture-content');
  const fileInput = document.getElementById('fileInput');
  if (fileInput) fileInput.value = '';
  const qrStatus = document.getElementById('qr-status');
  if (qrStatus) qrStatus.textContent = 'QR_CEDULA pendiente';
  QrHandler.value = '';
  if (preview) {
    preview.src = '';
    preview.classList.add('hidden');
  }
  if (captureContent) {
    captureContent.classList.remove('hidden');
    captureContent.innerHTML = '<span class="material-symbols-outlined text-[40px] text-[#6B7280] opacity-60 mb-4">document_scanner</span><p class="text-[13px] text-[#6B7280] max-w-[190px]">Posicione el documento frente a la camara</p><p class="text-[11px] italic text-[#9CA3AF] max-w-[170px] mt-1">Arrastre o haga clic para subir</p>';
  }

  stopCamera();
}

function selectDocType(type) {
  Paso1State.currentDocType = type;
  document.querySelectorAll('.doc-type-btn').forEach((btn) => {
    const value = btn.dataset.docType;
    btn.classList.toggle('active', value === type);
  });
}

function handleFile(file) {
  const preview = document.getElementById('preview');
  const captureContent = document.getElementById('capture-content');
  if (!preview || !captureContent) return;

  hidePaso1Error();

  if (file.type.startsWith('image/')) {
    const reader = new FileReader();
    reader.onload = (event) => {
      preview.src = event.target.result;
      preview.classList.remove('hidden');
      captureContent.classList.add('hidden');
    };
    reader.readAsDataURL(file);
  } else {
    preview.classList.add('hidden');
    captureContent.classList.remove('hidden');
    captureContent.innerHTML = '<span class="material-symbols-outlined text-[40px] text-[#6B7280]">picture_as_pdf</span><p class="text-[13px] text-[#6B7280] mt-2">' + file.name + '</p>';
  }
}

async function scanDocument() {
if (!Paso1State.currentFile) return;
  const overlay = document.getElementById('scan-overlay');
  overlay.style.display = 'flex';
  actualizarEstadoOCR('procesando', 'Procesando OCR...');
  try {
    const data = await ApiDocument.scan(Paso1State.currentFile, null, mapDocType(Paso1State.currentDocType));
    fillFormFromOCR(data);
    if (data.status === 'complete' || (data.overall_confidence && data.overall_confidence > 0)) {
      actualizarEstadoOCR('completo', 'Datos extraídos correctamente');
    } else {
      actualizarEstadoOCR('detectado', 'Documento detectado - verifique los datos');
    }
  } catch (error) {
    showPaso1Error('Error al escanear: ' + error.message);
    actualizarEstadoOCR('error', 'Error al procesar documento');
  } finally {
    overlay.style.display = 'none';
  }
}

async function toggleCamera() {
  const modal = document.getElementById("cameraModal");
  const video = document.getElementById("videoPreview");
  const guideCanvas = document.getElementById("canvasGuide");

  try {
    // Obtener lista de todas las cámaras disponibles
    const devices = await navigator.mediaDevices.enumerateDevices();
    const cameras = devices.filter(d => d.kind === "videoinput");

    // Buscar DroidCam primero (prioridad), luego cualquier otra
    const droidcam = cameras.find(c =>
      c.label.toLowerCase().includes("droidcam")
    );
    const selectedCamera = droidcam || cameras[cameras.length - 1];

    console.log("Cámaras disponibles:", cameras.map(c => c.label));
    console.log("Usando:", selectedCamera?.label || "default");

    const constraints = {
      video: selectedCamera
        ? {
            deviceId: { exact: selectedCamera.deviceId },
            width: { ideal: 1920 },
            height: { ideal: 1080 },
          }
        : {
            width: { ideal: 1920 },
            height: { ideal: 1080 },
          },
    };

    const stream = await navigator.mediaDevices.getUserMedia(constraints);
    Paso1State.cameraStream = stream;
    video.srcObject = stream;

    await new Promise((resolve) => {
      video.onloadedmetadata = () => resolve();
    });

    _cameraGuide = new CameraGuide(video, guideCanvas);
    _cameraGuide.calcGuideRect();
    _cameraGuide.startLoop();

    modal.classList.add("active");

    document.getElementById("btnCapturarCamera")
      .addEventListener("click", capturePhoto, { once: true });
    document.getElementById("btnCancelarCamera")
      .addEventListener("click", stopCamera, { once: true });

  } catch (err) {
    console.error("Error accediendo a la cámara:", err);
    alert("No se pudo acceder a la cámara. Verifique los permisos.");
  }
}

function stopCamera() {
  const video = document.getElementById('videoPreview');
  const modal = document.getElementById('cameraModal');
  const processing = document.getElementById('cameraProcessing');

  if (_cameraGuide) {
    _cameraGuide.stopLoop();
    _cameraGuide = null;
  }

  if (Paso1State.cameraStream) {
    Paso1State.cameraStream.getTracks().forEach((track) => track.stop());
    Paso1State.cameraStream = null;
  }

  if (video) {
    video.srcObject = null;
  }

  if (modal) {
    modal.classList.remove('active');
  }
  if (processing) {
    processing.style.display = 'none';
  }
}

async function capturePhoto() {
  const video = document.getElementById('videoPreview');
  const canvasCapture = document.getElementById('canvasCapture');
  const processing = document.getElementById('cameraProcessing');
  const preview = document.getElementById('preview');
  const captureContent = document.getElementById('capture-content');

  if (!_cameraGuide || !video || !canvasCapture) return;

  try {
    await _cameraGuide.showReadyFeedback();

    if (processing) {
      processing.style.display = 'flex';
    }

    canvasCapture.width = video.videoWidth;
    canvasCapture.height = video.videoHeight;
    const captureCtx = canvasCapture.getContext('2d');
    captureCtx.drawImage(video, 0, 0);

    const blob = await new Promise((resolve) => {
      canvasCapture.toBlob(resolve, 'image/jpeg', 0.92);
    });

    stopCamera();

    const formData = new FormData();
    formData.append('front', blob, 'capture.jpg');

    const cropResponse = await fetch('http://localhost:8002/crop', {
      method: 'POST',
      body: formData,
    });

    if (!cropResponse.ok) {
      throw new Error('Error en /crop: ' + cropResponse.status);
    }

    const cropData = await cropResponse.json();

    if (preview && cropData.cropped_image) {
      preview.src = cropData.cropped_image;
      preview.classList.remove('hidden');
      if (captureContent) {
        captureContent.classList.add('hidden');
      }
    }

    const croppedBlob = await fetch(cropData.cropped_image).then((r) => r.blob());
    Paso1State.currentFile = new File([croppedBlob], 'captura.jpg', { type: 'image/jpeg' });

    if (processing) {
      processing.style.display = 'none';
    }

    await scanDocument();
  } catch (err) {
    console.error('Error en capturePhoto:', err);
    if (processing) {
      processing.style.display = 'none';
    }
    showPaso1Error('Error procesando la imagen. Intente nuevamente.');
  }
}

// ═══════════════════════════════════════════════════════════════════════════
// Funciones IP Webcam
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Abre el modal de IP Webcam.
 * No conecta automáticamente — el operador debe presionar "Conectar"
 * para evitar intentos fallidos si la IP cambió.
 */
function toggleIpWebcam() {
  const modal = document.getElementById("ipWebcamModal");
  modal.style.display = "flex";

  // Intentar conectar automáticamente con la IP guardada
  const savedUrl = sessionStorage.getItem("ipWebcamUrl");
  if (savedUrl) {
    document.getElementById("ipWebcamUrl").value = savedUrl;
    connectIpWebcam();
  }
}

/**
 * Conecta al servidor IP Webcam y comienza el polling de frames.
 * Primero verifica que el servidor esté disponible vía /shot.jpg
 * luego inicia el loop de refresco de imagen.
 */
async function connectIpWebcam() {
  const urlInput = document.getElementById("ipWebcamUrl");
  const statusDot = document.getElementById("ipStatusDot");
  const statusText = document.getElementById("ipStatusText");
  const btnConnect = document.getElementById("btnIpConnect");
  const btnCapture = document.getElementById("btnIpCapture");
  const frame = document.getElementById("ipWebcamFrame");
  const placeholder = document.getElementById("ipWebcamPlaceholder");
  const guideCanvas = document.getElementById("ipWebcamGuide");

  let baseUrl = urlInput.value.trim().replace(/\/$/, "");
  if (!baseUrl.startsWith("http")) baseUrl = "http://" + baseUrl;

  // Guardar URL en sesión para reconexión automática
  sessionStorage.setItem("ipWebcamUrl", baseUrl);

  // UI: estado "conectando"
  statusDot.style.background = "#f59e0b";
  statusText.textContent = "Conectando...";
  btnConnect.disabled = true;
  btnConnect.textContent = "Conectando...";

  // Detener polling anterior si existía
  _stopIpPolling();

  // ── Verificar conectividad con /shot.jpg ──
  const testUrl = baseUrl + "/shot.jpg?t=" + Date.now();
  let connected = false;

  try {
    const testImg = new Image();
    connected = await new Promise((resolve) => {
      testImg.onload = () => resolve(true);
      testImg.onerror = () => resolve(false);
      setTimeout(() => resolve(false), 4000);
      testImg.src = testUrl;
    });
  } catch (e) {
    connected = false;
  }

  btnConnect.disabled = false;
  btnConnect.textContent = "Conectar";

  if (!connected) {
    statusDot.style.background = "#ef4444";
    statusText.textContent = "Sin conexión — verifica la IP";
    statusText.style.color = "#ef4444";
    _ipWebcamConnected = false;
    return;
  }

  // ── Conexión exitosa ──────────────────────────────────────────────────
  _ipWebcamConnected = true;

  statusDot.style.background = "#00C896";
  statusText.textContent = "Conectado";
  statusText.style.color = "#00C896";

  frame.style.display = "block";
  placeholder.style.display = "none";

  btnCapture.disabled = false;
  btnCapture.style.background = "#00C896";
  btnCapture.style.color = "#fff";
  btnCapture.style.cursor = "pointer";

  guideCanvas.style.display = "block";

  // ── Iniciar polling de frames ─────────────────────────────────────────
  const intervalMs = Math.round(1000 / IP_WEBCAM_FPS);

  _ipWebcamInterval = setInterval(() => {
    if (!_ipWebcamConnected) return;

    const newSrc = baseUrl + "/shot.jpg?t=" + Date.now();
    frame.src = newSrc;

    frame.onerror = () => {
      _handleIpDisconnect();
    };

    frame.onload = () => {
      _drawIpGuideOverlay();
    };
  }, intervalMs);

  // Primer frame inmediato
  frame.src = baseUrl + "/shot.jpg?t=" + Date.now();
  frame.onerror = () => _handleIpDisconnect();
}

/**
 * Captura el frame actual del stream IP Webcam, lo envía al backend
 * para recorte con OpenCV y luego al pipeline OCR.
 */
async function captureIpWebcam() {
  if (!_ipWebcamConnected) return;

  const urlInput = document.getElementById("ipWebcamUrl");
  const processing = document.getElementById("ipWebcamProcessing");
  const baseUrl = urlInput.value.trim().replace(/\/$/, "");

  processing.style.display = "flex";

  // Pausar polling durante la captura
  _stopIpPolling();

  try {
    const shotUrl = baseUrl + "/shot.jpg?t=" + Date.now();
    const response = await fetch(shotUrl);

    if (!response.ok) {
      throw new Error("HTTP " + response.status + " al capturar frame");
    }

    const imageBlob = await response.blob();

    // ── Enviar al backend /crop para recorte con OpenCV ─────────────────
    const formData = new FormData();
    formData.append("front", imageBlob, "ip_capture.jpg");

    const cropResponse = await fetch("http://localhost:8002/crop", {
      method: "POST",
      body: formData,
    });

    if (!cropResponse.ok) {
      throw new Error("Error en /crop: " + cropResponse.status);
    }

    const cropData = await cropResponse.json();

    // ── Cerrar modal IP Webcam ──────────────────────────────────────────
    stopIpWebcam();

    // ── Actualizar preview con imagen recortada ─────────────────────────
    const previewEl = document.getElementById("preview");
    const captureContent = document.getElementById("capture-content");
    if (previewEl && cropData.cropped_image) {
      previewEl.src = cropData.cropped_image;
      previewEl.classList.remove("hidden");
      if (captureContent) {
        captureContent.classList.add("hidden");
      }
    }

    // ── Convertir base64 a Blob, setear como archivo actual y ejecutar OCR ──
    const croppedBlob = await fetch(cropData.cropped_image).then((r) => r.blob());
    Paso1State.currentFile = new File([croppedBlob], "ip_capture.jpg", { type: "image/jpeg" });

    await scanDocument();
  } catch (err) {
    console.error("Error en captureIpWebcam:", err);
    processing.style.display = "none";

    if (_ipWebcamConnected) {
      connectIpWebcam();
    }

    showPaso1Error("Error al capturar: " + err.message + ". Intente nuevamente.");
  }
}

/**
 * Cierra el modal IP Webcam y detiene el polling.
 */
function stopIpWebcam() {
  _stopIpPolling();
  _ipWebcamConnected = false;

  const modal = document.getElementById("ipWebcamModal");
  const processing = document.getElementById("ipWebcamProcessing");
  const btnCapture = document.getElementById("btnIpCapture");

  modal.style.display = "none";
  processing.style.display = "none";

  btnCapture.disabled = true;
  btnCapture.style.background = "#555";
  btnCapture.style.color = "#999";
  btnCapture.style.cursor = "not-allowed";
}

/**
 * Detiene el intervalo de polling de frames.
 */
function _stopIpPolling() {
  if (_ipWebcamInterval) {
    clearInterval(_ipWebcamInterval);
    _ipWebcamInterval = null;
  }
}

/**
 * Maneja la desconexión inesperada de la cámara IP.
 */
function _handleIpDisconnect() {
  if (!_ipWebcamConnected) return;

  _ipWebcamConnected = false;
  _stopIpPolling();

  const statusDot = document.getElementById("ipStatusDot");
  const statusText = document.getElementById("ipStatusText");
  const btnCapture = document.getElementById("btnIpCapture");

  statusDot.style.background = "#ef4444";
  statusText.textContent = "Conexión perdida — reconectando...";
  statusText.style.color = "#ef4444";
  btnCapture.disabled = true;
  btnCapture.style.background = "#555";
  btnCapture.style.cursor = "not-allowed";

  setTimeout(() => {
    if (document.getElementById("ipWebcamModal").style.display !== "none") {
      connectIpWebcam();
    }
  }, 3000);
}

/**
 * Dibuja el recuadro guía sobre el canvas del modal IP Webcam.
 * Reutiliza la clase CameraGuide con el elemento <img> como referencia.
 */
function _drawIpGuideOverlay() {
  const frame = document.getElementById("ipWebcamFrame");
  const guideCanvas = document.getElementById("ipWebcamGuide");

  if (!frame || !guideCanvas || !window.CameraGuide) return;

  const rect = frame.getBoundingClientRect();
  if (rect.width === 0) return;

  guideCanvas.width = rect.width;
  guideCanvas.height = rect.height;

  const tempGuide = new CameraGuide(frame, guideCanvas);
  tempGuide.calcGuideRect();
  tempGuide.drawOverlay(false);
}

async function cargarPuestos() {
  try {
    const response = await fetch('http://localhost:7000/api/puestos');
    const puestos = await response.json();
    const select = document.getElementById('f-puesto');
    if (!select) return;
    select.innerHTML = '<option value="">Seleccionar puesto...</option>';
    puestos.forEach((puesto) => {
      const option = document.createElement('option');
      option.value = puesto.id;
      option.textContent = puesto.nombrePuesto + ' - ' + puesto.ciudad;
      select.appendChild(option);
    });
  } catch (error) {
    console.error('Error cargando puestos:', error);
  }
}

function hydratePaso1FromSession() {
  const data = VotanteSession.getDatosRegistro();
  if (!data) return;

  const mappings = {
    'f-identificacion': data.identificacion,
    'f-primernombre': data.primerNombre,
    'f-segundonombre': data.segundoNombre,
    'f-primerapellido': data.primerApellido,
    'f-segundoapellido': data.segundoApellido,
    'f-correo': data.correo,
    'f-rol': data.idRol,
    'f-puesto': data.idPuesto
  };

  Object.entries(mappings).forEach(([id, value]) => {
    const input = document.getElementById(id);
    if (input && value !== undefined && value !== null && value !== '') {
      input.value = String(value);
      input.classList.add('filled');
    }
  });
}

async function enviarPreRegistro() {
  const data = {
    identificacion: document.getElementById('f-identificacion').value.trim(),
    primerNombre: document.getElementById('f-primernombre').value.trim(),
    segundoNombre: document.getElementById('f-segundonombre').value.trim(),
    primerApellido: document.getElementById('f-primerapellido').value.trim(),
    segundoApellido: document.getElementById('f-segundoapellido').value.trim(),
    correo: document.getElementById('f-correo').value.trim(),
    idRol: parseInt(document.getElementById('f-rol').value, 10) || null,
    idPuesto: parseInt(document.getElementById('f-puesto').value, 10) || null,
    fechaNacimiento: document.getElementById('f-fecha-nacimiento').value || null,
    consentimiento: VotanteSession.getConsentimiento()
  };

  if (!data.identificacion || !data.primerNombre || !data.primerApellido || !data.correo || !data.idRol || !data.idPuesto) {
    showPaso1Error('Por favor complete todos los campos obligatorios.');
    return;
  }

  if (!data.correo.includes('@') || data.correo.split('@').length !== 2 || !data.correo.split('@')[1].includes('.')) {
    showPaso1Error('Ingrese un correo electronico valido (debe contener @ y un dominio).');
    return;
  }

  hidePaso1Error();
  const button = document.getElementById('btn-continuar');
  if (button) {
    button.disabled = true;
    button.innerHTML = 'Enviando...';
  }

  try {
    await ApiPreRegistro.crear(data);
    VotanteSession.setDatosRegistro(data);
    VotanteSession.setIdentificacion(data.identificacion);
    Router.paso2Completo = false;
    Router.paso3Completo = false;
    Router.paso4Confirmado = false;
    await Router.irA(2);
  } catch (error) {
    showPaso1Error('Error al registrar: ' + error.message);
  } finally {
    if (button) {
      button.disabled = false;
      button.innerHTML = 'Continuar a captura de rostro<span class="material-symbols-outlined">arrow_forward</span>';
    }
  }
}

function initPaso1() {
  Paso1State.currentDocType = 'CC';
  Paso1State.currentFile = null;
  stopCamera();
  hidePaso1Error();

  const dropZone = document.getElementById('dropZone');
  const fileInput = document.getElementById('fileInput');
  const uploadButton = document.getElementById('btn-upload');
  const resetButton = document.getElementById('btn-reset');
  const continueButton = document.getElementById('btn-continuar');
  const docButtons = document.querySelectorAll('.doc-type-btn');

  docButtons.forEach((button) => {
    button.addEventListener('click', () => selectDocType(button.dataset.docType));
  });

  uploadButton.addEventListener('click', () => fileInput.click());
  dropZone.addEventListener('click', () => fileInput.click());
  dropZone.addEventListener('dragover', (event) => {
    event.preventDefault();
    dropZone.classList.add('dragover');
  });
  dropZone.addEventListener('dragleave', () => dropZone.classList.remove('dragover'));
  dropZone.addEventListener('drop', async (event) => {
    event.preventDefault();
    dropZone.classList.remove('dragover');
    const file = event.dataTransfer.files[0];
    if (!file) return;
    Paso1State.currentFile = file;
    handleFile(file);
    await scanDocument();
  });

  fileInput.addEventListener('change', async () => {
const file = fileInput.files[0];
    if (!file) return;
    Paso1State.currentFile = file;
    handleFile(file);
    actualizarEstadoOCR('detectado', 'Documento detectado');
    await scanDocument();
  });

  document.getElementById('btnCamera').addEventListener('click', toggleCamera);
  resetButton.addEventListener('click', resetForm);
  continueButton.addEventListener('click', enviarPreRegistro);

  document.querySelectorAll('.form-field').forEach((field) => {
    field.addEventListener('input', () => {
      field.classList.toggle('filled', !!field.value);
    });
    field.addEventListener('change', () => {
      field.classList.toggle('filled', !!field.value);
    });
  });

  selectDocType('CC');
  QrHandler.initPreRegistro();
  cargarPuestos().then(hydratePaso1FromSession);
}

function setPaso2State(message, tone) {
  const status = document.getElementById('bio-status');
  const dot = document.getElementById('bio-status-dot');
  status.textContent = message;
  dot.className = 'w-2.5 h-2.5 rounded-full';
  if (tone === 'success') {
    dot.classList.add('bg-green-500');
  } else if (tone === 'error') {
    dot.classList.add('bg-red-500');
  } else {
    dot.classList.add('bg-amber-400', 'animate-pulse');
  }
}

let fotoStream = null;
let fotoCapturada = false;
let fotoBlob = null;

async function abrirCamaraFoto() {
  const video = document.getElementById('foto-video');
  const guide = document.getElementById('foto-guide');
  try {
    fotoStream = await navigator.mediaDevices.getUserMedia({
      video: { width: 640, height: 480, facingMode: 'user' }
    });
    video.srcObject = fotoStream;
    video.classList.remove('hidden');
    document.getElementById('foto-preview').classList.add('hidden');
    document.getElementById('foto-placeholder').classList.add('hidden');
    document.getElementById('foto-guide').classList.remove('hidden');
    document.getElementById('btn-abrir-camara').classList.add('hidden');
    document.getElementById('btn-capturar-foto').classList.remove('hidden');
    document.getElementById('btn-repetir-foto').classList.add('hidden');
  } catch (e) {
    const errorBox = document.getElementById('bio-error');
    errorBox.textContent = 'No se pudo acceder a la cámara: ' + e.message;
    errorBox.classList.remove('hidden');
  }
}

function capturarFoto() {
  const video = document.getElementById('foto-video');
  const canvas = document.getElementById('foto-canvas');
  const preview = document.getElementById('foto-preview');
  const guide = document.getElementById('foto-guide');

  if (video.videoWidth === 0 || video.videoHeight === 0) {
    const errorBox = document.getElementById('bio-error');
    errorBox.textContent = 'La cámara no está lista. Por favor abre la cámara primero.';
    errorBox.classList.remove('hidden');
    return;
  }

  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  canvas.getContext('2d').drawImage(video, 0, 0);

  canvas.toBlob(blob => {
    fotoBlob = blob;
    fotoCapturada = true;

    if (fotoStream) {
      fotoStream.getTracks().forEach(t => t.stop());
      fotoStream = null;
    }

    const fotoUrl = URL.createObjectURL(blob);
    preview.src = fotoUrl;
    preview.classList.remove('hidden');
    video.classList.add('hidden');
    guide.classList.add('hidden');

    const data = VotanteSession.getDatosRegistro();
    if (data) {
      data.fotoPreview = fotoUrl;
      VotanteSession.setDatosRegistro(data);
    }

    setTimeout(() => {
      try {
        actualizarEstadoBiometria('completado', 'estado-foto-capturada');
      } catch(e) { console.warn('Estado foto:', e.message); }
      
      const fotoReview = document.getElementById('foto-review');
      if (fotoReview) fotoReview.classList.remove('hidden');

      const btnCapturar = document.getElementById('btn-capturar-foto');
      if (btnCapturar) btnCapturar.classList.add('hidden');
      
      const btnRepetir = document.getElementById('btn-repetir-foto');
      if (btnRepetir) btnRepetir.classList.remove('hidden');

      const warning = document.getElementById('foto-required-warning');
      if (warning) warning.classList.add('hidden');

      const btnEnroll = document.getElementById('btn-enroll');
      if (btnEnroll) {
        btnEnroll.disabled = false;
        btnEnroll.classList.remove('opacity-50', 'cursor-not-allowed');
      }
    }, 50);
  }, 'image/jpeg', 0.92);
}

function repetirFoto() {
  fotoCapturada = false;
  fotoBlob = null;
  document.getElementById('foto-preview').classList.add('hidden');
  document.getElementById('foto-placeholder').classList.remove('hidden');
  document.getElementById('btn-repetir-foto').classList.add('hidden');
  document.getElementById('btn-abrir-camara').classList.remove('hidden');
  document.getElementById('btn-capturar-foto').classList.add('hidden');
  document.getElementById('foto-required-warning').classList.remove('hidden');
  document.getElementById('btn-enroll').disabled = true;
  document.getElementById('btn-enroll').classList.add('opacity-50', 'cursor-not-allowed');
  document.getElementById('foto-status-badge').style.display = 'none';
  document.getElementById('foto-guide').classList.add('hidden');
  abrirCamaraFoto();
}

function initPaso2() {
  const data = VotanteSession.getDatosRegistro();
  const id = VotanteSession.getIdentificacion();
  if (!data || !id) {
    Router.irA(1);
    return;
  }

  fotoStream = null;
  fotoCapturada = false;
  fotoBlob = null;

  document.getElementById('bio-voter-name').textContent = [data.primerNombre, data.segundoNombre, data.primerApellido, data.segundoApellido].filter(Boolean).join(' ');
  document.getElementById('bio-voter-id').textContent = 'ID: ' + id;
  document.getElementById('bio-voter-role').textContent = data.idRol ? 'ROL ' + data.idRol : 'ROL PENDIENTE';
  document.getElementById('bio-progress-copy').textContent = 'Listo para iniciar enrolamiento';

  document.getElementById('btn-abrir-camara').addEventListener('click', abrirCamaraFoto);
  document.getElementById('btn-capturar-foto').addEventListener('click', capturarFoto);
  document.getElementById('btn-repetir-foto').addEventListener('click', repetirFoto);

  document.getElementById('btn-enroll').addEventListener('click', async () => {
    const errorBox = document.getElementById('bio-error');
    const button = document.getElementById('btn-enroll');
    errorBox.classList.add('hidden');
    button.disabled = true;

    const progressItems = [
      document.getElementById('bio-step-capture'),
      document.getElementById('bio-step-process'),
      document.getElementById('bio-step-confirm')
    ];

    const markProgress = (index, text) => {
      progressItems.forEach((item, current) => {
        const badge = item.querySelector('[data-progress-badge]');
        const label = item.querySelector('[data-progress-label]');
        if (current < index) {
          badge.innerHTML = '<span class="material-symbols-outlined text-[14px]">check</span>';
          badge.className = 'w-8 h-8 rounded-full bg-[#4CAF7D] text-white flex items-center justify-center text-[11px] font-bold';
          label.className = 'text-sm font-bold text-[#4CAF7D]';
        } else if (current === index) {
          badge.textContent = String(current + 1);
          badge.className = 'w-8 h-8 rounded-full bg-[#004D33] text-white flex items-center justify-center text-[11px] font-bold';
          label.className = 'text-sm font-bold text-[#004D33]';
        }
      });
      document.getElementById('bio-progress-copy').textContent = text;
    };

try {
      actualizarEstadoBiometria('completado', 'estado-documento');

      setPaso2State('Subiendo foto del rostro...', 'pending');
      markProgress(0, 'Subiendo foto del rostro...');
      await new Promise((resolve) => setTimeout(resolve, 300));

      const formData = new FormData();
      formData.append('foto', fotoBlob, 'rostro.jpg');
      formData.append('identificacion', id);

      const fotoRes = await fetch('http://localhost:7000/api/votantes/foto', {
        method: 'POST',
        body: formData
      });
      if (!fotoRes.ok) {
        throw new Error('Error subiendo foto del rostro');
      }

      actualizarEstadoBiometria('completado', 'estado-foto-guardada');

      setPaso2State('Capturando huellas biometria...', 'pending');
      markProgress(1, 'Capturando huellas biometria...');
      
      actualizarEstadoBiometria('actual', 'estado-huella');
      await new Promise((resolve) => setTimeout(resolve, 500));

      setPaso2State('Procesando plantillas...', 'pending');
      markProgress(2, 'Procesando plantillas...');
      await ApiEnrollment.enroll(id, false);

      actualizarEstadoBiometria('completado', 'estado-huella');

      setPaso2State('Enrolamiento confirmado', 'success');
      markProgress(3, 'Enrolamiento confirmado');
      progressItems.forEach((item) => {
        const badge = item.querySelector('[data-progress-badge]');
        const label = item.querySelector('[data-progress-label]');
        badge.innerHTML = '<span class="material-symbols-outlined text-[14px]">check</span>';
        badge.className = 'w-8 h-8 rounded-full bg-[#4CAF7D] text-white flex items-center justify-center text-[11px] font-bold';
        label.className = 'text-sm font-bold text-[#4CAF7D]';
      });

      document.getElementById('estado-guardando').classList.remove('hidden');
      await new Promise((resolve) => setTimeout(resolve, 800));
      document.getElementById('estado-guardando').classList.add('hidden');
      document.getElementById('estado-completo').classList.remove('hidden');

      document.getElementById('btn-enroll').classList.add('hidden');
      document.getElementById('btn-revisar-confirmar').classList.remove('hidden');

      Router.paso2Completo = true;
    } catch (error) {
      setPaso2State('Error en enrolamiento', 'error');
      errorBox.textContent = 'No fue posible completar el enrolamiento: ' + error.message;
      errorBox.classList.remove('hidden');
      button.disabled = false;
    }
  });

document.getElementById('btn-back-step1').addEventListener('click', () => Router.irA(1));

  document.getElementById('btn-revisar-confirmar').addEventListener('click', () => {
    Router.irA(3);
  });
}

function initPaso3() {
  const data = VotanteSession.getDatosRegistro();
  const id = VotanteSession.getIdentificacion();
  if (!data || !id) {
    Router.irA(1);
    return;
  }

document.getElementById('summary-fullname').textContent = [data.primerNombre, data.segundoNombre, data.primerApellido, data.segundoApellido].filter(Boolean).join(' ');
  document.getElementById('summary-identificacion').textContent = id;
  document.getElementById('summary-correo').textContent = data.correo || '--';
  document.getElementById('summary-rol').textContent = data.idRol ? String(data.idRol) : '--';
  document.getElementById('summary-puesto').textContent = data.idPuesto ? String(data.idPuesto) : '--';
  const fotoPreview = document.getElementById('summary-foto');
  const fotoPlaceholder = document.getElementById('summary-foto-placeholder');
  if (data.fotoPreview) {
    fotoPreview.src = data.fotoPreview;
    fotoPreview.classList.remove('hidden');
    fotoPlaceholder.classList.add('hidden');
  } else {
    fotoPreview.classList.add('hidden');
    fotoPlaceholder.classList.remove('hidden');
  }

  document.getElementById('btn-volver-editar').addEventListener('click', () => {
    Router.irA(1);
  });

  document.getElementById('btn-confirmar-registro').addEventListener('click', async () => {
    const overlay = document.getElementById('confirmando-overlay');
    overlay.classList.remove('hidden');

    await new Promise(resolve => setTimeout(resolve, 1000));

    Router.paso4Confirmado = true;
    Router.irA(5);
  });
}

function initPaso4() {
  const data = VotanteSession.getDatosRegistro();
  const id = VotanteSession.getIdentificacion();
  if (!data || !id) {
    Router.irA(1);
    return;
  }

  document.getElementById('completado-fullname').textContent = [data.primerNombre, data.segundoNombre, data.primerApellido, data.segundoApellido].filter(Boolean).join(' ');
  document.getElementById('completado-identificacion').textContent = id;
  document.getElementById('completado-correo').textContent = data.correo || '--';
  document.getElementById('completado-rol').textContent = data.idRol ? String(data.idRol) : '--';
  document.getElementById('completado-puesto').textContent = data.idPuesto ? String(data.idPuesto) : '--';

  document.getElementById('btn-otro-votante').addEventListener('click', async () => {
    VotanteSession.clear();
    Router.paso2Completo = false;
    Router.paso3Completo = false;
    Router.paso4Confirmado = false;
    await Router.irA(1);
  });

  document.getElementById('btn-ir-admin').addEventListener('click', () => {
    window.location.href = '/pages/admin/';
  });
}

window.Router = Router;
window.initPaso1 = initPaso1;
window.initPaso2 = initPaso2;
window.initPaso3 = initPaso3;
window.initPaso4 = initPaso4;
