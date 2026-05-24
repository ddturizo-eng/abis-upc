(function () {
  const state = {
    elecciones: [],
    eleccionId: '',
    tokens: [],
    auditoria: [],
    tab: 'tokens',
    estadoEnvio: '',
    search: ''
  };

  const $ = (id) => document.getElementById(id);
  const number = (value) => new Intl.NumberFormat('es-CO').format(Number(value || 0));
  const safe = (value) => String(value ?? '');
  const upper = (value) => safe(value).toUpperCase();
  const escapeHtml = (value) => safe(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');

  async function init() {
    bind();
    await cargarElecciones();
    await cargarTodo();
  }

  function bind() {
    $('contingencia-eleccion')?.addEventListener('change', async (event) => {
      state.eleccionId = event.target.value;
      await cargarTodo();
    });
    $('contingencia-refresh')?.addEventListener('click', cargarTodo);
    $('contingencia-emitir')?.addEventListener('click', emitirLote);
    $('contingencia-filter-envio')?.addEventListener('change', async (event) => {
      state.estadoEnvio = event.target.value;
      await cargarTokens();
    });
    $('contingencia-search')?.addEventListener('input', (event) => {
      state.search = event.target.value.trim().toLowerCase();
      renderTokens();
    });
    document.querySelectorAll('.cont-tab').forEach((button) => {
      button.addEventListener('click', () => setTab(button.dataset.tab));
    });
  }

  async function cargarElecciones() {
    const payload = await ApiElecciones.listar();
    state.elecciones = Array.isArray(payload) ? payload : (payload.data || payload.elecciones || []);
    const select = $('contingencia-eleccion');
    if (!select) return;
    select.innerHTML = state.elecciones.length
      ? state.elecciones.map((eleccion) => `<option value="${idEleccion(eleccion)}">${escapeHtml(nombreEleccion(eleccion))} - ${escapeHtml(estadoEleccion(eleccion))}</option>`).join('')
      : '<option value="">No hay elecciones registradas</option>';
    const activa = state.elecciones.find((item) => upper(estadoEleccion(item)) === 'EN_CURSO') || state.elecciones[0];
    state.eleccionId = activa ? String(idEleccion(activa)) : '';
    select.value = state.eleccionId;
  }

  async function cargarTodo() {
    if (!state.eleccionId) {
      renderEmpty();
      return;
    }
    await Promise.all([cargarResumen(), cargarTokens(), cargarAuditoria()]);
  }

  async function cargarResumen() {
    try {
      const data = await ApiContingencia.resumen(state.eleccionId);
      setText('cont-kpi-elegibles', number(data.elegibles));
      setText('cont-kpi-tokens', number(data.tokens));
      setText('cont-kpi-enviados', number(data.enviados));
      setText('cont-kpi-fallidos', number(data.fallidos));
      setText('cont-kpi-pendientes', number(data.pendientes));
    } catch (error) {
      showMessage(error.message || 'No fue posible cargar resumen.', 'error');
    }
  }

  async function cargarTokens() {
    try {
      state.tokens = await ApiContingencia.tokens(state.eleccionId, state.estadoEnvio);
      renderTokens();
    } catch (error) {
      $('contingencia-tokens-tbody').innerHTML = `<tr><td colspan="6" class="py-5 text-sm text-red-600">${escapeHtml(error.message)}</td></tr>`;
    }
  }

  async function cargarAuditoria() {
    try {
      state.auditoria = await ApiContingencia.auditoria(state.eleccionId, 150);
      renderAuditoria();
    } catch (error) {
      $('contingencia-auditoria-tbody').innerHTML = `<tr><td colspan="6" class="py-5 text-sm text-red-600">${escapeHtml(error.message)}</td></tr>`;
    }
  }

  async function emitirLote() {
    if (!state.eleccionId) return;
    const button = $('contingencia-emitir');
    button.disabled = true;
    button.innerHTML = '<i class="ti ti-loader-2 text-lg"></i> Emitiendo...';
    try {
      const result = await ApiContingencia.emitir(Number(state.eleccionId));
      showMessage(`Lote procesado: ${number(result.enviados)} enviados, ${number(result.fallidos)} fallidos.`, result.fallidos ? 'warning' : 'success');
      await cargarTodo();
    } catch (error) {
      showMessage(error.message || 'No fue posible emitir el lote.', 'error');
    } finally {
      button.disabled = false;
      button.innerHTML = '<i class="ti ti-send text-lg"></i> Emitir lote';
    }
  }

  async function tokenAction(idToken, action) {
    try {
      if (action === 'reenviar') await ApiContingencia.reenviar(idToken);
      if (action === 'revocar') await ApiContingencia.revocar(idToken);
      if (action === 'regenerar') await ApiContingencia.regenerar(idToken);
      showMessage('Operacion aplicada correctamente.', 'success');
      await cargarTodo();
    } catch (error) {
      showMessage(error.message || 'No fue posible aplicar la operacion.', 'error');
    }
  }

  function renderTokens() {
    const tbody = $('contingencia-tokens-tbody');
    if (!tbody) return;
    const rows = state.tokens.filter((token) => {
      if (!state.search) return true;
      return `${token.identificacion} ${token.nombre} ${token.correo}`.toLowerCase().includes(state.search);
    });
    tbody.innerHTML = rows.length ? rows.map((token) => `
      <tr class="border-b border-[#eef2ef] text-sm">
        <td class="py-3 pr-3">
          <strong class="block text-[#1f2925]">${escapeHtml(token.nombre || '--')}</strong>
          <span class="text-xs font-bold text-[#6b7280]">${escapeHtml(token.identificacion)}</span>
        </td>
        <td class="py-3 pr-3 text-[#5f6f67]">${escapeHtml(token.correo || '--')}</td>
        <td class="py-3 pr-3">
          <span class="rounded bg-[#f3f4f6] px-2 py-1 font-mono text-xs font-bold text-[#1f2925]">${escapeHtml(token.tokenHint || '--')}</span>
          <span class="ml-2 rounded px-2 py-1 text-xs font-extrabold ${tokenEstadoClass(token.estado)}">${escapeHtml(token.estado || '--')}</span>
        </td>
        <td class="py-3 pr-3">${estadoBadge(token.estadoEnvio)}<span class="mt-1 block text-xs text-[#6b7280]">${escapeHtml(token.fechaIntento || 'Sin intentos')}</span></td>
        <td class="py-3 pr-3 text-[#5f6f67]">${escapeHtml(token.fechaUso || 'No usado')}</td>
        <td class="py-3 text-right">
          <button class="rounded-md border border-[#dbe5df] px-3 py-2 text-xs font-extrabold text-[#075521]" data-action="reenviar" data-token="${token.idToken}">Reenviar</button>
          <button class="rounded-md border border-[#f3d0d0] px-3 py-2 text-xs font-extrabold text-[#b42318]" data-action="revocar" data-token="${token.idToken}">Revocar</button>
          <button class="rounded-md border border-[#dbe5df] px-3 py-2 text-xs font-extrabold text-[#1f2925]" data-action="regenerar" data-token="${token.idToken}">Regenerar</button>
        </td>
      </tr>
    `).join('') : '<tr><td colspan="6" class="py-6 text-center text-sm text-[#6b7280]">No hay tokens para la eleccion seleccionada.</td></tr>';

    tbody.querySelectorAll('[data-action]').forEach((button) => {
      button.addEventListener('click', () => tokenAction(button.dataset.token, button.dataset.action));
    });
  }

  function renderAuditoria() {
    const tbody = $('contingencia-auditoria-tbody');
    if (!tbody) return;
    tbody.innerHTML = state.auditoria.length ? state.auditoria.map((row) => `
      <tr class="border-b border-[#eef2ef] text-sm">
        <td class="py-3 pr-3 text-[#5f6f67]">${escapeHtml(row.fechaIntento || '--')}</td>
        <td class="py-3 pr-3"><strong>${escapeHtml(row.identificacion)}</strong></td>
        <td class="py-3 pr-3 text-[#5f6f67]">${escapeHtml(row.correoDestino || '--')}</td>
        <td class="py-3 pr-3">${estadoBadge(row.estadoEnvio)}</td>
        <td class="py-3 pr-3">${escapeHtml(row.intentoNumero || 1)}</td>
        <td class="py-3 text-[#5f6f67]">${escapeHtml(row.errorEnvio || row.messageId || '--')}</td>
      </tr>
    `).join('') : '<tr><td colspan="6" class="py-6 text-center text-sm text-[#6b7280]">Sin auditoria de envios.</td></tr>';
  }

  function setTab(tab) {
    state.tab = tab;
    document.querySelectorAll('.cont-tab').forEach((button) => {
      const active = button.dataset.tab === tab;
      button.classList.toggle('bg-white', active);
      button.classList.toggle('text-[#075521]', active);
      button.classList.toggle('text-[#5f6f67]', !active);
    });
    $('contingencia-panel-tokens').classList.toggle('hidden', tab !== 'tokens');
    $('contingencia-panel-auditoria').classList.toggle('hidden', tab !== 'auditoria');
  }

  function renderEmpty() {
    ['cont-kpi-elegibles', 'cont-kpi-tokens', 'cont-kpi-enviados', 'cont-kpi-fallidos', 'cont-kpi-pendientes'].forEach((id) => setText(id, '--'));
    $('contingencia-tokens-tbody').innerHTML = '<tr><td colspan="6" class="py-6 text-center text-sm text-[#6b7280]">Seleccione una eleccion.</td></tr>';
    $('contingencia-auditoria-tbody').innerHTML = '<tr><td colspan="6" class="py-6 text-center text-sm text-[#6b7280]">Seleccione una eleccion.</td></tr>';
  }

  function estadoBadge(estado) {
    const value = upper(estado || 'PENDIENTE');
    const klass = value === 'ENVIADO'
      ? 'bg-[#e8f5e9] text-[#075521]'
      : value === 'FALLIDO'
        ? 'bg-[#fee4e2] text-[#b42318]'
        : 'bg-[#fef3c7] text-[#92400e]';
    return `<span class="rounded px-2 py-1 text-xs font-extrabold ${klass}">${escapeHtml(value)}</span>`;
  }

  function tokenEstadoClass(estado) {
    const value = upper(estado);
    if (value === 'ACTIVO') return 'bg-[#e8f5e9] text-[#075521]';
    if (value === 'USADO') return 'bg-[#dbeafe] text-[#1e40af]';
    return 'bg-[#f3f4f6] text-[#6b7280]';
  }

  function showMessage(message, type = 'success') {
    const box = $('contingencia-message');
    if (!box) return;
    const styles = {
      success: 'border-[#b7e4c7] bg-[#e8f5e9] text-[#075521]',
      warning: 'border-amber-200 bg-amber-50 text-amber-700',
      error: 'border-red-200 bg-red-50 text-red-700'
    };
    box.className = `rounded-lg border p-3 text-sm font-bold ${styles[type] || styles.success}`;
    box.textContent = message;
    box.classList.remove('hidden');
  }

  function setText(id, value) {
    const el = $(id);
    if (el) el.textContent = value;
  }

  function idEleccion(eleccion) {
    return eleccion.idEleccion ?? eleccion.id_eleccion ?? eleccion.id;
  }

  function nombreEleccion(eleccion) {
    return eleccion.nombre || `Eleccion ${idEleccion(eleccion)}`;
  }

  function estadoEleccion(eleccion) {
    return eleccion.estado || '--';
  }

  init().catch((error) => showMessage(error.message || 'No fue posible iniciar contingencia.', 'error'));
})();
