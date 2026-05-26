const FaceCapture = {
  stream: null,
  previewUrl: null,

  init() {
    const data = VotanteSession.getDatosRegistro();
    if (!data || !VotanteSession.getIdentificacion()) {
      Router.irA(1);
      return;
    }

    document.getElementById('face-voter-name').textContent = [data.primerNombre, data.segundoNombre, data.primerApellido, data.segundoApellido].filter(Boolean).join(' ');
    document.getElementById('face-voter-id').textContent = 'ID: ' + VotanteSession.getIdentificacion();
    document.getElementById('btn-face-open').addEventListener('click', () => FaceCapture.openCamera());
    document.getElementById('btn-face-capture').addEventListener('click', () => FaceCapture.capture());
    document.getElementById('btn-face-repeat').addEventListener('click', () => FaceCapture.reset());
    document.getElementById('btn-face-back').addEventListener('click', () => Router.irA(1));
    document.getElementById('btn-face-continue').addEventListener('click', async () => {
      FaceCapture.stop();
      Router.paso2Completo = true;
      await Router.irA(3);
    });
    FaceCapture.setMessage('Abra la camara y capture la foto manualmente.', true);
  },

  async openCamera() {
    const video = document.getElementById('face-video');
    FaceCapture.hideError();
    try {
      FaceCapture.stream = await navigator.mediaDevices.getUserMedia({ video: { width: 960, height: 720, facingMode: 'user' } });
      video.srcObject = FaceCapture.stream;
      video.classList.remove('hidden');
      document.getElementById('face-placeholder').classList.add('hidden');
      document.getElementById('face-preview').classList.add('hidden');
      document.getElementById('btn-face-open').classList.add('hidden');
      document.getElementById('btn-face-capture').disabled = false;
      document.getElementById('btn-face-capture').classList.remove('opacity-50', 'cursor-not-allowed');
      FaceCapture.setMessage('Camara activa. Encuadre el rostro y capture manualmente.', true);
      document.getElementById('face-status').textContent = 'Camara activa';
    } catch (error) {
      FaceCapture.showError('No se pudo acceder a la camara.');
    }
  },

  frameToBlob() {
    return new Promise((resolve) => {
      const video = document.getElementById('face-video');
      const canvas = document.getElementById('face-canvas');
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      canvas.getContext('2d').drawImage(video, 0, 0);
      canvas.toBlob(resolve, 'image/jpeg', 0.9);
    });
  },

  async capture() {
    const video = document.getElementById('face-video');
    if (!video || !video.videoWidth) {
      FaceCapture.showError('La camara aun no esta lista.');
      return;
    }

    const button = document.getElementById('btn-face-capture');
    button.disabled = true;
    button.classList.add('opacity-50', 'cursor-not-allowed');
    FaceCapture.hideError();

    try {
      const blob = await FaceCapture.frameToBlob();
      FaceCapture.previewUrl = URL.createObjectURL(blob);

      const upload = new FormData();
      upload.append('foto', blob, 'rostro.jpg');
      upload.append('identificacion', VotanteSession.getIdentificacion());
      const saveRes = await fetch('http://localhost:7000/api/votantes/foto', { method: 'POST', body: upload });
      if (!saveRes.ok) throw new Error('No fue posible guardar la foto.');
      const saved = await saveRes.json();

      const data = VotanteSession.getDatosRegistro();
      data.fotoPreview = FaceCapture.previewUrl;
      data.fotoUrl = saved.foto_url;
      VotanteSession.setDatosRegistro(data);

      FaceCapture.stop();
      document.getElementById('face-preview').src = FaceCapture.previewUrl;
      document.getElementById('face-preview').classList.remove('hidden');
      document.getElementById('face-placeholder').classList.add('hidden');
      document.getElementById('btn-face-repeat').classList.remove('hidden');
      document.getElementById('btn-face-continue').disabled = false;
      document.getElementById('btn-face-continue').classList.remove('opacity-50', 'cursor-not-allowed');
      FaceCapture.setMessage('Foto almacenada correctamente.', true);
      document.getElementById('face-status').textContent = 'Foto capturada';
    } catch (error) {
      FaceCapture.showError(error.message);
      button.disabled = false;
      button.classList.remove('opacity-50', 'cursor-not-allowed');
    }
  },

  reset() {
    FaceCapture.stop();
    document.getElementById('face-preview').classList.add('hidden');
    document.getElementById('btn-face-repeat').classList.add('hidden');
    document.getElementById('btn-face-open').classList.remove('hidden');
    document.getElementById('btn-face-capture').disabled = true;
    document.getElementById('btn-face-capture').classList.add('opacity-50', 'cursor-not-allowed');
    document.getElementById('btn-face-continue').disabled = true;
    document.getElementById('btn-face-continue').classList.add('opacity-50', 'cursor-not-allowed');
    document.getElementById('face-placeholder').classList.remove('hidden');
    FaceCapture.setMessage('Abra la camara y capture la foto manualmente.', true);
  },

  stop() {
    if (FaceCapture.stream) FaceCapture.stream.getTracks().forEach((track) => track.stop());
    FaceCapture.stream = null;
    const video = document.getElementById('face-video');
    if (video) {
      video.srcObject = null;
      video.classList.add('hidden');
    }
    const box = document.getElementById('face-box');
    if (box) box.classList.add('hidden');
  },

  setMessage(text, ok) {
    const message = document.getElementById('face-message');
    message.textContent = text;
    message.className = `mt-4 rounded-lg border p-3 text-sm ${ok ? 'border-green-200 bg-green-50 face-status-ok' : 'border-amber-200 bg-amber-50 face-status-warn'}`;
  },

  showError(text) {
    if (window.showToast) window.showToast(text, 'error');
  },

  hideError() {}
};

window.FaceCapture = FaceCapture;
