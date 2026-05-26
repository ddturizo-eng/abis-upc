const FingerprintUI = {
  state: 'IDLE',
  total: 4,
  current: 0,
  minutiaTimer: null,
  socket: null,

  init() {
    document.getElementById('btn-fingerprint-start').addEventListener('click', () => FingerprintUI.start());
    document.getElementById('btn-fingerprint-back').addEventListener('click', () => Router.irA(2));
    document.getElementById('btn-fingerprint-continue').addEventListener('click', () => Router.irA(4));
    FingerprintUI.render('IDLE', 'Coloque su dedo indice sobre el lector');
  },

  async start() {
    const button = document.getElementById('btn-fingerprint-start');
    button.disabled = true;
    button.classList.add('opacity-60', 'cursor-not-allowed');
    FingerprintUI.current = 0;
    FingerprintUI.render('WAITING_FINGER', 'Preparando lector biometrico...');

    let finished = false;
    let enrollResult = null;
    let enrollError = null;
    await FingerprintUI.openSocket();

    const enrollRequest = FingerprintUI.enrollWithRetry(VotanteSession.getIdentificacion())
      .then((result) => {
        enrollResult = result;
      })
      .catch((error) => {
        enrollError = error;
      })
      .finally(() => {
        finished = true;
      });

    try {
      FingerprintUI.render('WAITING_FINGER', 'Esperando respuesta del lector biometrico...');

      await enrollRequest;

      if (enrollError) {
        throw enrollError;
      }

      if (!enrollResult || enrollResult.success !== true) {
        throw new Error(enrollResult?.detail || enrollResult?.error || 'El enrolamiento no fue confirmado por el backend.');
      }

      FingerprintUI.current = FingerprintUI.total;
      FingerprintUI.render('COMPLETE', enrollResult.message || 'Huella capturada correctamente');
      FingerprintUI.closeSocket();
      Router.paso3Completo = true;
      document.getElementById('btn-fingerprint-start').classList.add('hidden');
      document.getElementById('btn-fingerprint-continue').classList.remove('hidden');
    } catch (error) {
      FingerprintUI.render('ERROR', 'Error de lectura, intente nuevamente');
      FingerprintUI.closeSocket();
      if (window.showToast) window.showToast(error.message || 'No fue posible completar la captura. Intente nuevamente.', 'error');
      button.disabled = false;
      button.classList.remove('opacity-60', 'cursor-not-allowed');
    } finally {
      finished = true;
    }
  },

  async enrollWithRetry(identificacion) {
    try {
      return await ApiEnrollment.enroll(identificacion, false);
    } catch (error) {
      const message = error.message || '';
      if (message.includes('ya tiene plantilla biometrica')) {
        FingerprintUI.render('WAITING_FINGER', 'Reemplazando enrolamiento biometrico anterior...');
        return await ApiEnrollment.enroll(identificacion, true);
      }
      throw error;
    }
  },

  openSocket() {
    if (FingerprintUI.socket && FingerprintUI.socket.readyState <= WebSocket.OPEN) {
      return Promise.resolve();
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return new Promise((resolve) => {
      let settled = false;
      FingerprintUI.socket = new WebSocket(`${protocol}//${window.location.host}/ws/biometria-ui`);

      const finish = () => {
        if (!settled) {
          settled = true;
          resolve();
        }
      };

      FingerprintUI.socket.onopen = finish;
      FingerprintUI.socket.onmessage = (event) => {
        const progress = JSON.parse(event.data);
        FingerprintUI.applyProgress(progress);
      };

      FingerprintUI.socket.onerror = () => {
        if (FingerprintUI.state !== 'COMPLETE' && FingerprintUI.state !== 'ERROR') {
          FingerprintUI.render('WAITING_FINGER', 'Canal en vivo no disponible. Esperando confirmacion del backend...');
        }
        finish();
      };
      FingerprintUI.socket.onclose = finish;

      setTimeout(finish, 1200);
    });
  },

  closeSocket() {
    if (FingerprintUI.socket && FingerprintUI.socket.readyState === WebSocket.OPEN) {
      FingerprintUI.socket.close(1000, 'enrollment finished');
    }
    FingerprintUI.socket = null;
  },

  applyProgress(progress) {
    if (progress.identificacion && progress.identificacion !== VotanteSession.getIdentificacion()) {
      return;
    }

    FingerprintUI.current = Math.min(progress.samples || progress.step || 0, FingerprintUI.total);
    const message = progress.mensaje || progress.message || 'Esperando lector biometrico...';

    if (progress.estado === 'PROCESANDO_CAPTURA' || progress.state === 'capturing') {
      FingerprintUI.render('READING', message);
      FingerprintUI.animateMinutiae();
      return;
    }

    if (progress.estado === 'PROCESANDO_MINUCIAS' || progress.state === 'processing') {
      FingerprintUI.current = FingerprintUI.total;
      FingerprintUI.render('READING', message);
      FingerprintUI.animateMinutiae();
      return;
    }

    if (progress.estado === 'FINALIZADO_EXITOSO' || progress.state === 'complete') {
      FingerprintUI.current = FingerprintUI.total;
      FingerprintUI.render('COMPLETE', message);
      FingerprintUI.closeSocket();
      return;
    }

    if (progress.estado === 'ERROR' || progress.state === 'error') {
      FingerprintUI.render('ERROR', message);
      FingerprintUI.closeSocket();
      return;
    }

    FingerprintUI.render('WAITING_FINGER', message);
  },

  render(state, instruction) {
    FingerprintUI.state = state;
    const percent = Math.round((FingerprintUI.current / FingerprintUI.total) * 100);
    const orb = document.getElementById('fingerprint-orb');
    const bar = document.getElementById('fingerprint-bar');
    orb.style.setProperty('--fp-progress', `${percent}%`);
    orb.classList.toggle('reading', state === 'READING');
    orb.classList.toggle('complete', state === 'COMPLETE');
    bar.style.width = `${percent}%`;
    bar.className = `h-full rounded-full transition-all ${state === 'COMPLETE' ? 'bg-[#2e7d32]' : 'bg-[#4fc3f7]'}`;
    document.getElementById('fingerprint-count').textContent = `${FingerprintUI.current} / ${FingerprintUI.total} capturas`;
    document.getElementById('fingerprint-instruction').textContent = instruction;
  },

  animateMinutiae() {
    const layer = document.getElementById('minutia-layer');
    let created = 0;
    clearInterval(FingerprintUI.minutiaTimer);
    layer.innerHTML = '';
    FingerprintUI.minutiaTimer = setInterval(() => {
      if (created >= 8 || FingerprintUI.state !== 'READING') {
        clearInterval(FingerprintUI.minutiaTimer);
        return;
      }
      const point = document.createElement('span');
      point.className = 'minutia-point';
      point.style.left = `${38 + Math.random() * 25}%`;
      point.style.top = `${28 + Math.random() * 42}%`;
      layer.appendChild(point);
      created += 1;
    }, 90);
  },

  pause(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
};

window.FingerprintUI = FingerprintUI;
