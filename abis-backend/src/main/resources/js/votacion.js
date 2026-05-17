(function initVotacionKiosko() {
  const state = {
    eleccion: null,
    cargos: [],
    votante: null,
    permiso: null,
    idCandidato: null
  };

  const $ = (id) => document.getElementById(id);
  const screens = ['welcome', 'verify', 'identity', 'ballot', 'success'];

  function token() {
    return localStorage.getItem('abis_token') || '';
  }

  async function verificarSesion() {
    if (!token()) {
      window.location.replace('/pages/auth/login.html');
      return false;
    }
    try {
      const response = await fetch('/api/admin/dashboard', {
        headers: { Authorization: `Bearer ${token()}` }
      });
      if (!response.ok) throw new Error('Sesion invalida');
      return true;
    } catch (error) {
      localStorage.removeItem('abis_token');
      window.location.replace('/pages/auth/login.html');
      return false;
    }
  }

  function showScreen(name) {
    screens.forEach((screen) => {
      $(`screen-${screen}`).classList.toggle('hidden', screen !== name);
    });
  }

  function setMessage(id, message) {
    const element = $(id);
    if (!element) return;
    element.textContent = message || '';
    element.classList.toggle('hidden', !message);
  }

  async function cargarEleccion() {
    try {
      const data = await API.get('/api/votacion/activa');
      state.eleccion = data.eleccion;
      state.cargos = data.cargos || [];
      const nombre = state.eleccion?.nombre || 'jornada electoral';
      $('welcome-election-name').textContent = nombre;
      $('ballot-election-name').textContent = nombre;
      renderTarjeton();
      setMessage('welcome-error', '');
    } catch (error) {
      $('welcome-election-name').textContent = 'jornada electoral';
      setMessage('welcome-error', error.message || 'No hay una elección en curso.');
      $('btn-go-verify').disabled = true;
    }
  }

  async function iniciarVerificacion() {
    setMessage('verify-error', '');
    $('verify-status').textContent = 'Capturando huella. Mantén el dedo sobre el lector.';
    $('btn-start-verify').disabled = true;

    try {
      const result = await API.post('/api/verify', {});
      if (!result?.matched || !result.identificacion) {
        throw new Error('No se encontró coincidencia biométrica.');
      }

      const identificacion = String(result.identificacion);
      const [votante, permiso] = await Promise.all([
        API.get('/api/votacion/votante?identificacion=' + encodeURIComponent(identificacion)),
        API.get(`/api/votantes/${encodeURIComponent(identificacion)}/puede-votar?idEleccion=${encodeURIComponent(state.eleccion.id)}`)
      ]);

      state.votante = votante;
      state.permiso = permiso;
      renderIdentidad();
      showScreen('identity');
    } catch (error) {
      setMessage('verify-error', error.message || 'No fue posible verificar la huella.');
      $('verify-status').textContent = 'Verificación no completada. Intenta nuevamente.';
    } finally {
      $('btn-start-verify').disabled = false;
    }
  }

  function renderIdentidad() {
    const votante = state.votante || {};
    const permiso = state.permiso || {};
    $('identity-name').textContent = votante.nombre || '--';
    $('identity-id').textContent = votante.identificacion || '--';
    $('identity-status').textContent = permiso.puede === 'S' ? 'HABILITADO PARA VOTAR' : (permiso.motivo || votante.estado || '--');
    $('identity-place').textContent = votante.idPuesto ? `Puesto ${votante.idPuesto}` : '--';
    $('jury-confirm-identity').checked = false;
    $('jury-confirm-identity').disabled = permiso.puede !== 'S';
    $('btn-go-ballot').disabled = true;
    setMessage('identity-error', permiso.puede === 'S' ? '' : (permiso.motivo || 'El votante no puede votar en esta elección.'));

    if (votante.fotoUrl) {
      $('identity-photo').src = votante.fotoUrl;
      $('identity-photo').classList.remove('hidden');
      $('identity-photo-empty').classList.add('hidden');
    } else {
      $('identity-photo').classList.add('hidden');
      $('identity-photo-empty').classList.remove('hidden');
    }
  }

  function renderTarjeton() {
    if (!state.cargos.length) {
      $('vote-ballot').innerHTML = '<div class="kiosk-message kiosk-message-error">La elección activa no tiene candidatos configurados.</div>';
      return;
    }

    $('vote-ballot').innerHTML = state.cargos.map((grupo) => `
      <div class="kiosk-cargo">
        <div class="kiosk-cargo-title">
          <i class="ti ti-ballot" aria-hidden="true"></i>
          <span>
            <small>CARGO A ELEGIR</small>
            ${escapeHtml(grupo.cargo || 'Cargo')}
          </span>
        </div>
        <div class="kiosk-candidates">
          ${(grupo.candidatos || []).map((candidato) => `
            <label class="kiosk-candidate">
              <input type="radio" name="candidato" value="${Number(candidato.idCandidato)}">
              <span class="kiosk-candidate-check" aria-hidden="true"><i class="ti ti-check"></i></span>
              <span class="kiosk-candidate-number">${escapeHtml(formatNumeroCampania(candidato.numeroCampania))}</span>
              <span class="kiosk-candidate-meta">
                <small>MOVIMIENTO</small>
                <span class="kiosk-candidate-party">${escapeHtml(candidato.movimiento || candidato.partido || 'Universitario')}</span>
              </span>
              <span class="kiosk-candidate-photo" aria-hidden="true">
                <i class="ti ti-user"></i>
              </span>
              <span class="kiosk-candidate-name">${escapeHtml(candidato.nombre || 'Candidato')}</span>
              <span class="kiosk-candidate-slogan">${escapeHtml(candidato.eslogan || 'Compromiso con la comunidad universitaria')}</span>
              <span class="kiosk-candidate-button">Seleccionar voto</span>
            </label>
          `).join('')}
        </div>
      </div>
    `).join('');

    document.querySelectorAll('#vote-ballot input[type="radio"]').forEach((input) => {
      input.addEventListener('change', () => {
        state.idCandidato = Number(input.value);
        $('vote-selection-status').textContent = 'Selección lista para confirmar';
        $('btn-vote-submit').disabled = false;
      });
    });
  }

  function irATarjeton() {
    state.idCandidato = null;
    $('vote-selection-status').textContent = 'Seleccione una opción';
    $('btn-vote-submit').disabled = true;
    document.querySelectorAll('#vote-ballot input[type="radio"]').forEach((input) => {
      input.checked = false;
    });
    showScreen('ballot');
  }

  async function registrarVoto() {
    if (!state.votante || !state.eleccion || !state.idCandidato) {
      return;
    }

    if (!confirm('El voto se registrará de forma irreversible. ¿Continuar?')) {
      return;
    }

    try {
      $('btn-vote-submit').disabled = true;
      await API.post('/api/votos/registrar', {
        identificacion: state.votante.identificacion,
        idEleccion: state.eleccion.id,
        idCandidato: state.idCandidato,
        idPuesto: state.votante.idPuesto
      });
      showScreen('success');
      window.setTimeout(volverInicio, 4000);
    } catch (error) {
      setMessage('identity-error', error.message || 'No fue posible registrar el voto.');
      showScreen('identity');
    }
  }

  function volverInicio() {
    state.votante = null;
    state.permiso = null;
    state.idCandidato = null;
    $('verify-status').textContent = 'Cuando estés listo, inicia la verificación.';
    setMessage('verify-error', '');
    setMessage('identity-error', '');
    document.querySelectorAll('#vote-ballot input[type="radio"]').forEach((input) => {
      input.checked = false;
    });
    showScreen('welcome');
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  async function init() {
    if (!(await verificarSesion())) return;
    $('btn-go-verify').addEventListener('click', () => showScreen('verify'));
    $('btn-start-verify').addEventListener('click', iniciarVerificacion);
    $('btn-cancel-verify').addEventListener('click', () => showScreen('welcome'));
    $('jury-confirm-identity').addEventListener('change', () => {
      $('btn-go-ballot').disabled = !$('jury-confirm-identity').checked;
    });
    $('btn-go-ballot').addEventListener('click', irATarjeton);
    $('btn-retry-identity').addEventListener('click', () => showScreen('verify'));
    $('btn-ballot-back')?.addEventListener('click', () => showScreen('identity'));
    $('btn-vote-submit').addEventListener('click', registrarVoto);
    $('btn-return-welcome').addEventListener('click', volverInicio);
    await cargarEleccion();
    showScreen('welcome');
  }

  init();

  function formatNumeroCampania(value) {
    if (value === null || value === undefined || value === '') return '--';
    const text = String(value);
    return /^\d+$/.test(text) ? text.padStart(2, '0') : text;
  }
})();
