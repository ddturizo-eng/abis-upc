(function initVotacionKiosko() {
  const state = {
    eleccion: null,
    cargos: [],
    votante: null,
    permiso: null,
    idCandidato: null,
    seleccionRealizada: false
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
    document.body.classList.toggle('welcome-lock', name !== 'ballot');
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
        throw new Error('No hay un votante que coincida con la biometria capturada. Retira el dedo, verifica que sea el votante correcto e intenta nuevamente.');
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
      setMessage('verify-error', error.message || 'No hay un votante que coincida con la biometria capturada. Intenta nuevamente.');
      $('verify-status').textContent = 'Verificacion no completada. No se encontro coincidencia biometrica.';
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

    const cargoActual = state.cargos.length === 1
      ? `CARGO A ELEGIR: ${state.cargos[0].cargo || 'Cargo'}`
      : `CARGOS A ELEGIR: ${state.cargos.length}`;
    if ($('ballot-current-cargo')) {
      $('ballot-current-cargo').textContent = cargoActual;
    }

    $('vote-ballot').innerHTML = state.cargos.map((grupo, grupoIndex) => {
      const cargo = grupo.cargo || 'Cargo';
      const candidatos = opcionesTarjeton(grupo);
      return `
        <section class="kiosk-cargo ballot-document">
          <header class="ballot-document-header">
            <div class="ballot-document-brand">
              <span class="flag-strip" aria-hidden="true"><span></span><span></span><span></span></span>
              <strong>Elecciones Universitarias</strong>
              <em>${new Date().getFullYear()}</em>
            </div>
            <div class="ballot-document-title">
              <small>Voto por la formula de</small>
              <h3>${escapeHtml(cargo)}</h3>
              <span>${escapeHtml(state.eleccion?.nombre || 'Eleccion universitaria')} - Unicesar</span>
            </div>
            <div class="ballot-document-code" aria-label="Codigo de verificacion">
              <span class="barcode-lines" aria-hidden="true"></span>
              <strong>ABIS${String(grupoIndex + 1).padStart(4, '0')}</strong>
            </div>
          </header>
          <div class="ballot-document-instruction">
            Marque solo una opcion de su preferencia
          </div>
          <div class="kiosk-candidates">
            ${candidatos.map((candidato, index) => renderOpcionTarjeton(candidato, grupo, index)).join('')}
          </div>
          <footer class="ballot-document-footer">
            <span><i class="ti ti-shield-check" aria-hidden="true"></i> Sistema Electoral - Unicesar</span>
            <span>Documento oficial de votacion electronica</span>
          </footer>
        </section>
      `;
    }).join('');

    document.querySelectorAll('#vote-ballot input[type="radio"]').forEach((input) => {
      input.addEventListener('change', () => {
        state.idCandidato = input.dataset.votoBlanco === 'true' ? null : Number(input.value);
        state.seleccionRealizada = true;
        $('vote-selection-status').textContent = 'Seleccion lista para confirmar';
        $('btn-vote-submit').disabled = false;
      });
    });
  }

  function irATarjeton() {
    state.idCandidato = null;
    state.seleccionRealizada = false;
    $('vote-selection-status').textContent = 'Seleccione una opción';
    $('btn-vote-submit').disabled = true;
    document.querySelectorAll('#vote-ballot input[type="radio"]').forEach((input) => {
      input.checked = false;
    });
    document.querySelectorAll('.tarjeton-candidato').forEach((card) => card.classList.remove('seleccionado', 'selected'));
    showScreen('ballot');
  }

  async function registrarVoto() {
    if (!state.votante || !state.eleccion || !state.seleccionRealizada) {
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
    state.seleccionRealizada = false;
    $('verify-status').textContent = 'Cuando estés listo, inicia la verificación.';
    setMessage('verify-error', '');
    setMessage('identity-error', '');
    document.querySelectorAll('#vote-ballot input[type="radio"]').forEach((input) => {
      input.checked = false;
    });
    document.querySelectorAll('.tarjeton-candidato').forEach((card) => card.classList.remove('seleccionado', 'selected'));
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

  function opcionesTarjeton(grupo) {
    const candidatos = [...(grupo.candidatos || [])];
    const tieneVotoBlanco = candidatos.some((candidato) =>
      candidato.idCandidato == null ||
      /voto\s+en\s+blanco/i.test(String(candidato.nombre || candidato.nombreCompleto || ''))
    );
    if (!tieneVotoBlanco) {
      candidatos.push({
        idCandidato: null,
        numeroCampania: '',
        nombre: 'Voto en blanco',
        cargo: grupo.cargo || 'Cargo',
        movimiento: 'Opcion oficial',
        esVotoBlanco: true
      });
    }
    return candidatos;
  }

  function renderOpcionTarjeton(candidato, grupo, index) {
    const esVotoBlanco = candidato.esVotoBlanco || candidato.idCandidato == null;
    const foto = candidato.fotoUrl || candidato.foto || candidato.foto_url || '';
    const nombre = candidato.nombre || candidato.nombreCompleto || 'Candidato';
    const movimiento = candidato.movimiento || candidato.partido || (esVotoBlanco ? 'Opcion oficial' : 'Universitario');
    return `
      <label class="kiosk-candidate tarjeton-candidato${esVotoBlanco ? ' blank-vote' : ''}" style="animation-delay:${index * 0.06}s">
        <input type="radio" name="candidato" value="${candidato.idCandidato == null ? '' : Number(candidato.idCandidato)}" data-voto-blanco="${esVotoBlanco ? 'true' : 'false'}">
        <span class="kiosk-candidate-check" aria-hidden="true"><i class="ti ti-check"></i></span>
        <span class="candidate-top">
          <span class="kiosk-candidate-number">${esVotoBlanco ? 'VB' : escapeHtml(formatNumeroCampania(candidato.numeroCampania))}</span>
        </span>
        <span class="kiosk-candidate-photo">
          ${esVotoBlanco
            ? '<span class="blank-vote-label">Voto en blanco</span>'
            : `<img src="${escapeHtml(foto)}" alt="Foto de ${escapeHtml(nombre)}" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
              <span class="img-fallback" style="${foto ? 'display:none;' : ''}">
                <span class="img-fallback-avatar">${escapeHtml(initials(nombre))}</span>
                <span>Sin fotografia</span>
              </span>`}
        </span>
        <span class="candidate-bottom">
          <span class="kiosk-candidate-role">${escapeHtml(grupo.cargo || candidato.cargo || 'Cargo')}</span>
          <span class="kiosk-candidate-name">${escapeHtml(nombre)}</span>
          <span class="kiosk-candidate-party"><i class="ti ${esVotoBlanco ? 'ti-square-dashed' : 'ti-flag'}" aria-hidden="true"></i>${escapeHtml(movimiento)}</span>
        </span>
      </label>
    `;
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

  function initials(value) {
    return String(value || '?')
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part.charAt(0))
      .join('')
      .toUpperCase();
  }
})();
