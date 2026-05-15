(function initVotacion() {
  const state = { eleccion: null, cargos: [], votante: null, selecciones: {} };
  const $ = (id) => document.getElementById(id);

  function showError(message) {
    $('vote-error').textContent = message;
    $('vote-error').classList.remove('hidden');
    $('vote-success').classList.add('hidden');
  }

  function showSuccess(message) {
    $('vote-success').textContent = message;
    $('vote-success').classList.remove('hidden');
    $('vote-error').classList.add('hidden');
  }

  function updateSubmit() {
    const completas = state.cargos.length > 0 && state.cargos.every((cargo) => state.selecciones[cargo.cargo]);
    const ok = completas && state.votante && !$('vote-confirm-identity').disabled && $('vote-confirm-identity').checked;
    $('btn-vote-submit').disabled = !ok;
    $('btn-vote-submit').classList.toggle('opacity-50', !ok);
    $('btn-vote-submit').classList.toggle('cursor-not-allowed', !ok);
  }

  async function cargarTarjeton() {
    try {
      const data = await API.get('/api/votacion/activa');
      state.eleccion = data.eleccion;
      state.cargos = data.cargos || [];
      $('vote-election-name').textContent = state.eleccion.nombre;
      renderTarjeton();
    } catch (error) {
      showError(error.message);
      $('vote-election-name').textContent = 'No hay eleccion activa';
    }
  }

  function renderTarjeton() {
    $('vote-ballot').innerHTML = state.cargos.map((grupo) => `
      <div class="rounded-lg border border-slate-200 overflow-hidden">
        <div class="bg-slate-50 px-4 py-3 text-sm font-bold text-[#004D33]">${grupo.cargo}</div>
        <div class="grid grid-cols-2 gap-3 p-4">
          ${(grupo.candidatos || []).map((c) => `
            <label class="flex cursor-pointer items-center gap-3 rounded-lg border border-slate-200 p-4 hover:border-[#004D33]">
              <input type="radio" name="cargo-${grupo.cargo}" value="${c.idCandidato}" data-cargo="${grupo.cargo}" class="text-[#004D33]">
              <span class="flex h-10 w-10 items-center justify-center rounded-full bg-[#004D33] text-sm font-bold text-white">${c.numeroCampania}</span>
              <span class="font-bold text-slate-700">${c.nombre}</span>
            </label>
          `).join('')}
        </div>
      </div>
    `).join('');
    document.querySelectorAll('#vote-ballot input[type="radio"]').forEach((input) => {
      input.addEventListener('change', () => {
        state.selecciones[input.dataset.cargo] = Number(input.value);
        updateSubmit();
      });
    });
  }

  async function buscarVotante() {
    const identificacion = $('vote-identificacion').value.trim();
    if (!identificacion) {
      showError('Ingrese la identificacion del votante.');
      return;
    }
    try {
      const votante = await API.get('/api/votacion/votante?identificacion=' + encodeURIComponent(identificacion));
      state.votante = votante;
      $('vote-voter-card').classList.remove('hidden');
      $('vote-voter-name').textContent = votante.nombre;
      $('vote-voter-state').textContent = votante.yaVoto ? 'VOTO YA EJERCIDO' : votante.estado;
      $('vote-confirm-identity').checked = false;
      $('vote-confirm-identity').disabled = votante.yaVoto || votante.estado !== 'PENDIENTE';
      if (votante.fotoUrl) {
        $('vote-voter-photo').src = votante.fotoUrl;
        $('vote-voter-photo').classList.remove('hidden');
        $('vote-voter-photo-empty').classList.add('hidden');
      } else {
        $('vote-voter-photo').classList.add('hidden');
        $('vote-voter-photo-empty').classList.remove('hidden');
      }
      if (votante.yaVoto) showError('Voto ya ejercido.');
      else if (votante.estado !== 'PENDIENTE') showError('Votante no habilitado.');
      else showSuccess('Votante validado. Confirme identidad visual.');
      updateSubmit();
    } catch (error) {
      state.votante = null;
      $('vote-voter-card').classList.add('hidden');
      showError(error.message);
      updateSubmit();
    }
  }

  async function registrarVoto() {
    const selecciones = Object.entries(state.selecciones).map(([cargo, idCandidato]) => ({ cargo, idCandidato }));
    if (!confirm('El voto se registrara de forma irreversible. Continuar?')) return;
    try {
      await API.post('/api/votacion/registrar', {
        identificacion: state.votante.identificacion,
        selecciones
      });
      showSuccess('Voto registrado correctamente.');
      state.votante = null;
      state.selecciones = {};
      $('vote-identificacion').value = '';
      $('vote-voter-card').classList.add('hidden');
      document.querySelectorAll('#vote-ballot input[type="radio"]').forEach((input) => { input.checked = false; });
      updateSubmit();
    } catch (error) {
      showError(error.message);
    }
  }

  $('btn-vote-search').addEventListener('click', buscarVotante);
  $('vote-identificacion').addEventListener('keydown', (event) => {
    if (event.key === 'Enter') buscarVotante();
  });
  $('vote-confirm-identity').addEventListener('change', updateSubmit);
  $('btn-vote-submit').addEventListener('click', registrarVoto);
  cargarTarjeton();
})();

