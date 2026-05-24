(function initJuradosModule() {
  const JuradosState = {
    tabActivo: 'asignacion',
    poolConfig: {
      roles: ['ESTUDIANTE', 'DOCENTE', 'ADMINISTRATIVO'],
      estados: ['PENDIENTE'],
      excluirCandidatos: true,
      requerirBiometrico: true,
      soloNoVotaron: false
    },
    distribucionConfig: {
      modo: 'fijo_por_puesto',
      valorFijo: 3,
      porcentaje: 5,
      totalManual: 0,
      distribucion: 'equitativa',
      cargos: { PRESIDENTE: 1, VOCAL: 2, SECRETARIO: 1 },
      puestoAsignado: 'distinto'
    },
    turnos: [{ inicio: '08:00', fin: '12:00' }],
    poolElegibles: [],
    votantes: [],
    mesas: [],
    puestos: [],
    elecciones: [],
    idEleccion: null,
    puestoSeleccionado: null,
    simulacionResultado: null,
    mesaManualActual: null,
    resumenAsignacion: null,
    poolElegibleVista: []
  };
  let recalculoTimer = null;

  const $ = (id) => document.getElementById(id);
  const number = (value) => new Intl.NumberFormat('es-CO').format(Number(value) || 0);
  const apiData = (payload) => {
    const value = payload?.data ?? payload ?? [];
    if (typeof value === 'string') {
      try { return JSON.parse(value); } catch (error) { return []; }
    }
    return Array.isArray(value) ? value : [];
  };
  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[char]));

  function nombreVotante(v) {
    return [v.primer_nombre, v.segundo_nombre, v.primer_apellido, v.segundo_apellido, v.nombreCompleto]
      .filter(Boolean).join(' ').replace(/\s+/g, ' ').trim() || 'Sin nombre';
  }

  function rolNombre(v) {
    const roles = { 1: 'ESTUDIANTE', 2: 'DOCENTE', 3: 'EGRESADO', 4: 'ADMINISTRATIVO' };
    return v.rol || roles[v.rol_id] || roles[v.idRol] || 'SIN ROL';
  }

  function puestoId(p) {
    return p.idPuesto ?? p.id_puesto ?? p.id ?? p.ID_PUESTO;
  }

  function puestoNombre(p) {
    return p.nombrePuesto ?? p.nombre_puesto ?? p.nombrePuestoVotacion ?? p.nombre_puesto_votacion ?? `Puesto ${puestoId(p)}`;
  }

  async function initJurados() {
    bindEvents();
    renderTurnos();
    resetMetricas();
    mostrarSkeletonMetricas(true);
    await Promise.all([cargarPuestos(), cargarVotantes(), cargarElecciones()]);
    await cargarMesas();
    await recalcularAsignacion();
    mostrarSkeletonMetricas(false);
    renderPuestos();
    renderPlanillaFiltros();
    generarPreviewPlanilla();
  }

  function bindEvents() {
    document.querySelectorAll('.jurados-tab').forEach((button) => {
      button.addEventListener('click', () => switchTab(button.dataset.tab));
    });

    document.querySelectorAll('.role-chip').forEach((chip) => {
      chip.addEventListener('click', () => {
        const input = chip.querySelector('input');
        input.checked = !input.checked;
        chip.classList.toggle('active', input.checked);
        programarRecalculo();
      });
    });

    document.querySelectorAll('.state-card').forEach((card) => {
      card.addEventListener('click', () => {
        selectExclusiveCard('.state-card', card, 'input[name="estadoPool"]');
        programarRecalculo();
      });
    });

    document.querySelectorAll('.mode-card').forEach((card) => {
      card.addEventListener('click', () => {
        selectExclusiveCard('.mode-card', card, 'input[name="modoAsignacion"]');
        programarRecalculo();
      });
    });

    ['excluirCandidatos', 'requerirBiometrico', 'soloNoVotaron'].forEach((id) => {
      $(id)?.addEventListener('change', programarRecalculo);
    });

    $('juradosPorPuesto')?.addEventListener('input', programarRecalculo);
    $('decrementJurados')?.addEventListener('click', () => ajustarJuradosPorMesa(-1));
    $('incrementJurados')?.addEventListener('click', () => ajustarJuradosPorMesa(1));
    $('btnRefreshSummary')?.addEventListener('click', () => {
      programarRecalculo();
    });
    $('btnVerPool')?.addEventListener('click', abrirPoolElegible);
    $('buscarPoolElegible')?.addEventListener('input', () => renderPoolElegible($('buscarPoolElegible').value));
    $('btnSimular')?.addEventListener('click', simularAsignacion);
    $('btnEjecutarAsignacion')?.addEventListener('click', ejecutarAsignacion);
    $('btnConfirmarAsignacion')?.addEventListener('click', confirmarSimulacion);
    $('btnNuevaMesa')?.addEventListener('click', () => abrirModalMesa());
    $('formMesa')?.addEventListener('submit', guardarMesaDesdeForm);
    $('buscarVotanteJurado')?.addEventListener('input', debounce(() => buscarVotanteParaJurado($('buscarVotanteJurado').value), 300));
    $('planillaPuesto')?.addEventListener('change', () => { renderPlanillaFiltros(); generarPreviewPlanilla(); });
    $('planillaMesa')?.addEventListener('change', () => generarPreviewPlanilla());
    $('btnPreviewPDF')?.addEventListener('click', () => window.print());
    $('btnExportarPDF')?.addEventListener('click', async () => {
      try {
        await exportarPlanillaPDF($('planillaPuesto').value, $('planillaMesa').value);
      } catch (error) {
        mostrarNotificacion(error.message, 'error');
      }
    });
    $('btnNotificarTodos')?.addEventListener('click', () => enviarCorreosJurados(juradosFiltradosPlanilla()));
    document.querySelectorAll('[data-close-modal]').forEach((button) => {
      button.addEventListener('click', () => cerrarModal(button.dataset.closeModal));
    });
  }

  function selectExclusiveCard(selector, card, inputSelector) {
    document.querySelectorAll(selector).forEach((item) => {
      item.classList.toggle('active', item === card);
      const input = item.querySelector(inputSelector);
      if (input) input.checked = item === card;
    });
  }

  function ajustarJuradosPorMesa(delta) {
    const input = $('juradosPorPuesto');
    const next = Math.max(Number(input.min || 1), Math.min(Number(input.max || 20), Number(input.value || 3) + delta));
    input.value = next;
    programarRecalculo();
  }

  function programarRecalculo() {
    mostrarSkeletonMetricas(true);
    clearTimeout(recalculoTimer);
    recalculoTimer = setTimeout(() => {
      recalcularAsignacion().finally(() => mostrarSkeletonMetricas(false));
    }, 300);
  }

  async function recalcularAsignacion() {
    leerPoolConfig();
    leerDistribucionConfig();
    try {
      const response = await API.post('/api/jurados/resumen-asignacion', resumenRequestBody());
      const resumen = response?.data ?? response;
      JuradosState.resumenAsignacion = resumen;
      aplicarResumenBackend(resumen);
    } catch (error) {
      calcularPool();
      calcularTotalEstimado();
    }
  }

  function switchTab(tabId) {
    JuradosState.tabActivo = tabId;
    document.querySelectorAll('.jurados-tab').forEach((button) => button.classList.toggle('active', button.dataset.tab === tabId));
    document.querySelectorAll('.jurados-tab-panel').forEach((panel) => panel.classList.toggle('active', panel.id === `tab-${tabId}`));
  }

  async function cargarPuestos() {
    try {
      JuradosState.puestos = apiData(await API.get('/api/puestos'));
    } catch (error) {
      JuradosState.puestos = [];
    }
    if (!JuradosState.puestoSeleccionado && JuradosState.puestos.length) {
      JuradosState.puestoSeleccionado = String(puestoId(JuradosState.puestos[0]));
    }
    poblarSelectPuestos();
  }

  async function cargarVotantes() {
    try {
      JuradosState.votantes = apiData(await API.get('/api/votantes'));
    } catch (error) {
      JuradosState.votantes = [];
    }
  }

  async function cargarElecciones() {
    try {
      JuradosState.elecciones = apiData(await API.get('/api/elecciones'));
      const activa = JuradosState.elecciones.find((e) => e.estado === 'EN_CURSO') || JuradosState.elecciones[0];
      JuradosState.idEleccion = activa?.id || activa?.idEleccion || null;
    } catch (error) {
      JuradosState.elecciones = [];
    }
  }

  async function cargarMesas() {
    try {
      JuradosState.mesas = apiData(await API.get('/api/jurados/mesas'));
    } catch (error) {
      JuradosState.mesas = [];
    }
    renderPuestos();
    renderMesas();
    renderPlanillaFiltros();
    generarPreviewPlanilla();
  }

  function leerPoolConfig() {
    JuradosState.poolConfig.roles = [...document.querySelectorAll('#rolesPool input:checked')].map((input) => input.value);
    JuradosState.poolConfig.estados = [...document.querySelectorAll('#estadosPool input:checked')].map((input) => input.value);
    JuradosState.poolConfig.excluirCandidatos = Boolean($('excluirCandidatos')?.checked);
    JuradosState.poolConfig.requerirBiometrico = Boolean($('requerirBiometrico')?.checked);
    JuradosState.poolConfig.soloNoVotaron = Boolean($('soloNoVotaron')?.checked);
  }

  function leerDistribucionConfig() {
    JuradosState.distribucionConfig.modo = document.querySelector('input[name="modoAsignacion"]:checked')?.value || 'fijo_por_puesto';
    JuradosState.distribucionConfig.valorFijo = Number($('juradosPorPuesto')?.value || 3);
    JuradosState.distribucionConfig.porcentaje = Number($('porcentajeJurados')?.value || 5);
    JuradosState.distribucionConfig.totalManual = Number($('totalManual')?.value || 0);
    JuradosState.distribucionConfig.distribucion = $('distribucionPuestos')?.value || 'equitativa';
    JuradosState.distribucionConfig.puestoAsignado = document.querySelector('input[name="puestoAsignado"]:checked')?.value || 'distinto';
  }

  function calcularPool() {
    leerPoolConfig();
    if (!JuradosState.votantes.length) {
      JuradosState.poolElegibles = [];
      actualizarMetricas(0);
      return JuradosState.poolElegibles;
    }

    JuradosState.poolElegibles = JuradosState.votantes.filter((v) => {
      const rol = rolNombre(v);
      const estado = v.estado_voto || v.estadoVoto || 'PENDIENTE';
      const tieneBio = Boolean(v.biometrico || v.foto_url);
      if (!JuradosState.poolConfig.roles.includes(rol)) return false;
      if (!JuradosState.poolConfig.estados.includes(estado)) return false;
      if (JuradosState.poolConfig.requerirBiometrico && !tieneBio) return false;
      if (JuradosState.poolConfig.soloNoVotaron && estado === 'EJERCIDO') return false;
      return true;
    });
    actualizarMetricas(JuradosState.poolElegibles.length);
    return JuradosState.poolElegibles;
  }

  function calcularTotalEstimado() {
    leerDistribucionConfig();
    const mesas = mesasCount();
    const pool = poolCount();
    let total = JuradosState.distribucionConfig.valorFijo * mesas;

    if (JuradosState.distribucionConfig.modo === 'porcentaje') {
      total = pool ? Math.max(1, Math.ceil(pool * (JuradosState.distribucionConfig.porcentaje / 100))) : 0;
    }
    if (JuradosState.distribucionConfig.modo === 'total_manual') {
      total = JuradosState.distribucionConfig.totalManual || total;
    }

    $('totalEstimado').textContent = number(total);
    actualizarResumen(total);
    return total;
  }

  function actualizarMetricas(pool) {
    const mesas = mesasCount();
    const total = Number($('juradosPorPuesto')?.value || 3) * mesas;
    setText('metricPool', number(pool));
    setText('footerPool', number(pool));
    setText('metricMesas', number(mesas));
    setText('footerMesas', number(mesas));
    setText('metricJurados', number(total));
    setText('footerAsignar', number(total));
    setText('metricPoolHint', 'Disponibles');
    setText('metricJuradosHint', 'Total necesario');
    setText('metricMesasHint', 'En el proceso');
  }

  function actualizarResumen(total = calcularTotalEstimado()) {
    const pool = poolCount();
    const mesas = mesasCount();
    const assignable = Math.min(total, Math.max(0, pool - 10));
    const coverage = total ? Math.min(100, Math.round((assignable / total) * 100)) : 0;
    const completas = Math.max(0, Math.floor(mesas * Math.min(coverage, 100) / 100));
    const incompletas = Math.max(0, mesas - completas);
    const criticas = coverage < 65 ? Math.ceil(mesas * 0.1) : 0;

    setText('metricCobertura', coverage);
    setText('metricCoberturaUnit', '%');
    setText('metricCoberturaHint', 'Proyección actual');
    setText('summaryCoverage', coverage);
    setText('summaryCoverageUnit', '%');
    setText('summaryAssignable', number(assignable));
    setText('summaryRequired', number(total));
    setText('summaryComplete', `${number(completas)} (${mesas ? Math.round((completas / mesas) * 100) : 0}%)`);
    setText('summaryIncomplete', `${number(incompletas)} (${mesas ? Math.round((incompletas / mesas) * 100) : 0}%)`);
    setText('summaryCritical', `${number(criticas)} (${mesas ? Math.round((criticas / mesas) * 100) : 0}%)`);
    setText('summaryTotalJurados', number(total));
    setText('metricJurados', number(total));
    setText('footerAsignar', number(total));
    setText('distMesas3', number(completas));
    setText('distMesas2', number(incompletas));
    setText('distMesas1', number(criticas));
    setText('availabilityBadge', pool < total ? `Solo hay ${number(pool)} jurados elegibles disponibles para asignación` : 'Pool suficiente para la asignación');
    setText('alertIncomplete', number(incompletas));
    setText('alertConflicts', number(Math.ceil(incompletas * 3)));
    setText('legendEstudiantes', `${total ? 60 : 0}% (${number(Math.round(total * 0.6))})`);
    setText('legendDocentes', `${total ? 25 : 0}% (${number(Math.round(total * 0.25))})`);
    setText('legendAdministrativos', `${total ? 15 : 0}% (${number(Math.round(total * 0.15))})`);

    const bar = $('coverageBar');
    if (bar) {
      bar.style.setProperty('--target', `${coverage}%`);
      bar.classList.remove('animate');
      requestAnimationFrame(() => bar.classList.add('animate'));
    }
    const btn = $('btnEjecutarAsignacion');
    if (btn) btn.disabled = pool === 0;
    animateCounter('metricPool', pool);
    animateCounter('metricJurados', total);
    animateCounter('metricMesas', mesas);
    animateCounter('metricCobertura', coverage);
    animateCounter('summaryCoverage', coverage);
    animateCounter('summaryAssignable', assignable);
    animateDonut(total);
  }

  function aplicarResumenBackend(resumen) {
    const pool = Number(resumen.pool || 0);
    const mesas = Number(resumen.mesas || 0);
    const total = Number(resumen.juradosRequeridos || 0);
    const assignable = Number(resumen.asignables || 0);
    const coverage = Number(resumen.cobertura || 0);
    const completas = Number(resumen.completas || 0);
    const incompletas = Number(resumen.incompletas || 0);
    const criticas = Number(resumen.criticas || 0);
    const roles = resumen.roles || {};

    setText('metricPool', number(pool));
    setText('footerPool', number(pool));
    setText('metricMesas', number(mesas));
    setText('footerMesas', number(mesas));
    setText('metricJurados', number(total));
    setText('footerAsignar', number(total));
    setText('totalEstimado', number(total));
    setText('metricPoolHint', 'Desde base de datos');
    setText('metricJuradosHint', 'Calculado con mesas reales');
    setText('metricMesasHint', 'Registradas en BD');
    setText('metricCobertura', coverage);
    setText('metricCoberturaUnit', '%');
    setText('metricCoberturaHint', 'Según elegibles reales');
    setText('summaryCoverage', coverage);
    setText('summaryCoverageUnit', '%');
    setText('summaryAssignable', number(assignable));
    setText('summaryRequired', number(total));
    setText('summaryComplete', `${number(completas)} (${mesas ? Math.round((completas / mesas) * 100) : 0}%)`);
    setText('summaryIncomplete', `${number(incompletas)} (${mesas ? Math.round((incompletas / mesas) * 100) : 0}%)`);
    setText('summaryCritical', `${number(criticas)} (${mesas ? Math.round((criticas / mesas) * 100) : 0}%)`);
    setText('summaryTotalJurados', number(total));
    setText('distMesas3', number(completas));
    setText('distMesas2', number(incompletas));
    setText('distMesas1', number(criticas));
    setText('availabilityBadge', pool < total ? `Solo hay ${number(pool)} jurados elegibles disponibles para asignación` : 'Pool suficiente para la asignación');
    setText('alertIncomplete', number(incompletas));
    setText('alertConflicts', number(resumen.conflictos || 0));
    setRoleLegend('legendEstudiantes', roles.ESTUDIANTE || 0, pool);
    setRoleLegend('legendDocentes', roles.DOCENTE || 0, pool);
    setRoleLegend('legendAdministrativos', roles.ADMINISTRATIVO || 0, pool);

    const bar = $('coverageBar');
    if (bar) {
      bar.style.setProperty('--target', `${coverage}%`);
      bar.classList.remove('animate');
      requestAnimationFrame(() => bar.classList.add('animate'));
    }
    const btn = $('btnEjecutarAsignacion');
    if (btn) btn.disabled = pool === 0 || total === 0;
    animateCounter('metricPool', pool);
    animateCounter('metricJurados', total);
    animateCounter('metricMesas', mesas);
    animateCounter('metricCobertura', coverage);
    animateCounter('summaryCoverage', coverage);
    animateCounter('summaryAssignable', assignable);
    animateDonutFromRoles(roles, pool);
  }

  function renderTurnos() {
    $('turnosGrid').innerHTML = JuradosState.turnos.map((turno, index) => `
      <div class="turno-row">
        <label>Turno ${index + 1}</label>
        <input type="time" value="${turno.inicio}" data-turno="${index}" data-field="inicio" aria-label="Inicio turno ${index + 1}">
        <span>—</span>
        <input type="time" value="${turno.fin}" data-turno="${index}" data-field="fin" aria-label="Fin turno ${index + 1}">
        ${index === JuradosState.turnos.length - 1 ? '<button class="btn-outline" type="button" id="btnAgregarTurno"><i class="ti ti-plus"></i> Agregar turno</button>' : ''}
      </div>
    `).join('');
    document.querySelectorAll('[data-turno]').forEach((input) => {
      input.addEventListener('input', () => {
        JuradosState.turnos[Number(input.dataset.turno)][input.dataset.field] = input.value;
      });
    });
    $('btnAgregarTurno')?.addEventListener('click', agregarTurno);
  }

  function agregarTurno() {
    JuradosState.turnos.push({ inicio: '12:00', fin: '16:00' });
    renderTurnos();
  }

  function requestBody() {
    leerPoolConfig();
    leerDistribucionConfig();
    return {
      idEleccion: JuradosState.idEleccion,
      configuracion: {
        poolConfig: JuradosState.poolConfig,
        distribucionConfig: {
          ...JuradosState.distribucionConfig,
          totalEstimado: JuradosState.resumenAsignacion?.juradosRequeridos ?? estimarTotalLocal()
        },
        turnos: JuradosState.turnos
      }
    };
  }

  function resumenRequestBody() {
    return {
      idEleccion: JuradosState.idEleccion,
      configuracion: {
        poolConfig: JuradosState.poolConfig,
        distribucionConfig: JuradosState.distribucionConfig,
        turnos: JuradosState.turnos
      }
    };
  }

  function estimarTotalLocal() {
    const mesas = mesasCount();
    const pool = poolCount();
    if (JuradosState.distribucionConfig.modo === 'porcentaje') {
      return pool ? Math.max(1, Math.ceil(pool * (JuradosState.distribucionConfig.porcentaje / 100))) : 0;
    }
    if (JuradosState.distribucionConfig.modo === 'total_manual') {
      return JuradosState.distribucionConfig.totalManual || 0;
    }
    return JuradosState.distribucionConfig.valorFijo * mesas;
  }

  async function simularAsignacion() {
    try {
      const result = await API.post('/api/jurados/asignar-aleatorio?dry_run=true', requestBody());
      JuradosState.simulacionResultado = (result?.data ?? result)?.jurados || [];
      renderResultadoAsignacion('Resultado de simulación', JuradosState.simulacionResultado);
      abrirModal('modalResultado');
    } catch (error) {
      mostrarNotificacion(error.message, 'error');
    }
  }

  async function abrirPoolElegible() {
    leerPoolConfig();
    leerDistribucionConfig();
    $('poolElegibleLista').innerHTML = '<div class="badge-warning">Consultando pool elegible...</div>';
    setText('poolElegibleTotal', '— registros');
    abrirModal('modalPoolElegible');
    try {
      const response = await API.post('/api/jurados/pool-elegible', resumenRequestBody());
      JuradosState.poolElegibleVista = response?.data ?? response ?? [];
      if ($('buscarPoolElegible')) $('buscarPoolElegible').value = '';
      renderPoolElegible('');
    } catch (error) {
      $('poolElegibleLista').innerHTML = `<div class="badge-warning">${escapeHtml(error.message)}</div>`;
    }
  }

  function renderPoolElegible(query) {
    const term = String(query || '').toLowerCase().trim();
    const rows = JuradosState.poolElegibleVista.filter((v) => {
      const text = `${v.identificacion || ''} ${v.nombreCompleto || ''} ${v.rol || ''} ${v.estadoVoto || ''} ${v.puestoHabitual || ''} ${v.ciudad || ''} ${v.sede || ''}`.toLowerCase();
      return !term || text.includes(term);
    });
    setText('poolElegibleTotal', `${number(rows.length)} registros`);
    $('poolElegibleLista').innerHTML = `
      <table class="pool-table">
        <thead><tr><th>Identificación</th><th>Nombre</th><th>Rol</th><th>Estado</th><th>Puesto</th><th>Biometría</th></tr></thead>
        <tbody>
          ${rows.map((v) => `
            <tr>
              <td>${escapeHtml(v.identificacion)}</td>
              <td>${escapeHtml(v.nombreCompleto)}</td>
              <td>${escapeHtml(v.rol)}</td>
              <td>${escapeHtml(v.estadoVoto)}</td>
              <td>${escapeHtml(v.puestoHabitual || '')}</td>
              <td>${v.tieneBiometrico ? 'Sí' : 'No'}</td>
            </tr>
          `).join('') || '<tr><td colspan="6">No hay votantes elegibles con los filtros actuales.</td></tr>'}
        </tbody>
      </table>
    `;
  }

  async function ejecutarAsignacion() {
    await simularAsignacion();
  }

  async function confirmarSimulacion() {
    try {
      const result = await API.post('/api/jurados/asignar-aleatorio', requestBody());
      JuradosState.simulacionResultado = (result?.data ?? result)?.jurados || [];
      renderResultadoAsignacion('Asignación guardada', JuradosState.simulacionResultado);
      mostrarNotificacion('Asignación aleatoria guardada');
      await cargarMesas();
    } catch (error) {
      mostrarNotificacion(error.message, 'error');
    }
  }

  function renderResultadoAsignacion(titulo, jurados) {
    $('resultadoTitulo').textContent = titulo;
    $('resultadoAsignacion').innerHTML = `
      <div class="resultado-list">
        ${jurados.map((j) => `
          <div class="jurado-row">
            <div class="jurado-avatar">${initials(j.nombreCompleto)}</div>
            <div class="jurado-info">
              <span class="jurado-nombre">${escapeHtml(j.nombreCompleto)}</span>
              <span class="jurado-id">${escapeHtml(j.identificacion)} · ${escapeHtml(j.rol || '')}</span>
              <span class="jurado-puesto-original">${escapeHtml(j.puestoHabitual || '')} → ${escapeHtml(j.nombrePuesto || j.puestoAsignado || '')}</span>
            </div>
            <span class="jurado-cargo-badge">${escapeHtml(j.cargo)}</span>
          </div>
        `).join('') || '<div class="badge-warning">No hay jurados para asignar.</div>'}
      </div>
    `;
  }

  function renderPuestos() {
    if (!$('puestosList')) return;
    const byPuesto = new Map(JuradosState.mesas.map((m) => [String(m.idPuesto), JuradosState.mesas.filter((x) => String(x.idPuesto) === String(m.idPuesto))]));
    $('puestosList').innerHTML = JuradosState.puestos.map((puesto) => {
      const id = String(puestoId(puesto));
      const mesas = byPuesto.get(id) || [];
      const jurados = mesas.reduce((sum, mesa) => sum + (mesa.jurados || []).length, 0);
      return `
        <div class="puesto-card ${String(JuradosState.puestoSeleccionado) === id ? 'active' : ''}" onclick="seleccionarPuesto('${id}')">
          <span class="puesto-nombre">${escapeHtml(puestoNombre(puesto))}</span>
          <span class="puesto-stats">${number(mesas.length)} mesas · ${number(jurados)} jurados</span>
          <span class="${jurados ? 'badge-verde' : 'badge-warning'}">${jurados ? 'Con asignación' : 'Pendiente'}</span>
        </div>
      `;
    }).join('') || '<div class="badge-warning">No hay puestos registrados.</div>';
  }

  function seleccionarPuesto(id) {
    JuradosState.puestoSeleccionado = String(id);
    renderPuestos();
    renderMesas();
  }

  function renderMesas() {
    if (!$('mesasGrid')) return;
    const puesto = JuradosState.puestos.find((p) => String(puestoId(p)) === String(JuradosState.puestoSeleccionado));
    $('mesasPanelTitle').textContent = puesto ? `Mesas de ${puestoNombre(puesto)}` : 'Mesas del puesto';
    const mesas = JuradosState.mesas.filter((m) => String(m.idPuesto) === String(JuradosState.puestoSeleccionado));
    $('mesasGrid').innerHTML = mesas.map(renderMesaCard).join('') || '<div class="badge-warning">No hay mesas registradas para este puesto.</div>';
  }

  function renderMesaCard(mesa) {
    return `
      <article class="mesa-card">
        <div class="mesa-header">
          <div><span class="mesa-numero">Mesa ${String(mesa.idMesa).padStart(2, '0')}</span><span class="mesa-horario">${time(mesa.horaIngreso)} → ${time(mesa.horaSalida)}</span></div>
          <div class="mesa-actions">
            <button class="btn-icon" onclick="editarMesa(${mesa.idMesa})" aria-label="Editar mesa"><i class="ti ti-pencil"></i></button>
            <button class="btn-icon" onclick="eliminarMesa(${mesa.idMesa})" aria-label="Eliminar mesa"><i class="ti ti-x"></i></button>
          </div>
        </div>
        <div class="mesa-jurados-list">
          ${(mesa.jurados || []).map((j) => `
            <div class="jurado-row">
              <div class="jurado-avatar">${initials(j.nombreCompleto)}</div>
              <div class="jurado-info">
                <span class="jurado-nombre">${escapeHtml(j.nombreCompleto)}</span>
                <span class="jurado-id">${escapeHtml(j.identificacion)} · ${escapeHtml(j.rol || '')}</span>
                <span class="jurado-puesto-original">Puesto habitual: ${escapeHtml(j.puestoHabitual || '--')}</span>
              </div>
              <span class="jurado-cargo-badge">${escapeHtml(j.cargo)}</span>
              <button class="btn-icon" onclick="removerJurado('${escapeHtml(j.identificacion)}', ${mesa.idMesa})" aria-label="Remover jurado"><i class="ti ti-x"></i></button>
            </div>
          `).join('') || '<span class="badge-warning">Mesa sin jurados asignados</span>'}
          <div class="jurado-add-row" onclick="abrirModalAsignarJurado(${mesa.idMesa})">+ Asignar jurado manualmente</div>
        </div>
      </article>
    `;
  }

  function poblarSelectPuestos() {
    const options = JuradosState.puestos.map((p) => `<option value="${puestoId(p)}">${escapeHtml(puestoNombre(p))}</option>`).join('');
    if ($('mesaPuesto')) $('mesaPuesto').innerHTML = options;
    if ($('planillaPuesto')) $('planillaPuesto').innerHTML = '<option value="">Todos los puestos</option>' + options;
  }

  function abrirModalMesa(mesa = null) {
    $('modalMesaTitulo').textContent = mesa ? 'Editar mesa' : 'Nueva mesa';
    $('mesaId').value = mesa?.idMesa || '';
    $('mesaPuesto').value = mesa?.idPuesto || JuradosState.puestoSeleccionado || '';
    $('mesaHoraIngreso').value = time(mesa?.horaIngreso) || '08:00';
    $('mesaHoraSalida').value = time(mesa?.horaSalida) || '12:00';
    $('mesaCargo').value = mesa?.cargo || 'JURADOS';
    abrirModal('modalMesa');
  }

  function editarMesa(idMesa) {
    const mesa = JuradosState.mesas.find((m) => Number(m.idMesa) === Number(idMesa));
    if (mesa) abrirModalMesa(mesa);
  }

  async function guardarMesaDesdeForm(event) {
    event.preventDefault();
    const id = $('mesaId').value;
    const body = {
      idPuesto: Number($('mesaPuesto').value),
      horaIngreso: $('mesaHoraIngreso').value,
      horaSalida: $('mesaHoraSalida').value,
      cargo: $('mesaCargo').value
    };
    try {
      if (id) await API.request(`/api/jurados/mesas/${id}`, { method: 'PUT', body });
      else await API.post('/api/jurados/mesas', body);
      cerrarModal('modalMesa');
      await cargarMesas();
      recalcularAsignacion();
      mostrarNotificacion('Mesa guardada');
    } catch (error) {
      mostrarNotificacion(error.message, 'error');
    }
  }

  function eliminarMesa(idMesa) {
    const mesa = JuradosState.mesas.find((m) => Number(m.idMesa) === Number(idMesa));
    const count = (mesa?.jurados || []).length;
    confirmar(`¿Eliminar la Mesa ${idMesa}? ${count ? `Esta mesa tiene ${count} jurados asignados.` : ''}`, async () => {
      try {
        await API.request(`/api/jurados/mesas/${idMesa}${count ? '?force=true' : ''}`, { method: 'DELETE' });
        await cargarMesas();
        recalcularAsignacion();
        mostrarNotificacion('Mesa eliminada');
      } catch (error) {
        mostrarNotificacion(error.message, 'error');
      }
    });
  }

  function abrirModalAsignarJurado(idMesa) {
    JuradosState.mesaManualActual = idMesa;
    $('manualFecha').value = new Date().toISOString().slice(0, 10);
    $('buscarVotanteJurado').value = '';
    $('manualResults').innerHTML = '<span class="badge-warning">Escriba para buscar votantes.</span>';
    abrirModal('modalAsignar');
  }

  async function buscarVotanteParaJurado(query) {
    const term = String(query || '').toLowerCase().trim();
    if (term.length < 2) {
      $('manualResults').innerHTML = '<span class="badge-warning">Ingrese al menos 2 caracteres.</span>';
      return;
    }
    const juradosIds = new Set(JuradosState.mesas.flatMap((m) => (m.jurados || []).map((j) => String(j.identificacion))));
    const results = JuradosState.votantes.filter((v) => `${v.identificacion} ${nombreVotante(v)}`.toLowerCase().includes(term)).slice(0, 12);
    $('manualResults').innerHTML = results.map((v) => {
      const assigned = juradosIds.has(String(v.identificacion));
      return `
        <div class="jurado-row">
          <div class="jurado-avatar">${initials(nombreVotante(v))}</div>
          <div class="jurado-info">
            <span class="jurado-nombre">${escapeHtml(nombreVotante(v))}</span>
            <span class="jurado-id">${escapeHtml(v.identificacion)} · ${escapeHtml(rolNombre(v))} · ${escapeHtml(v.estado_voto || '')}</span>
            <span class="jurado-puesto-original">${assigned ? 'Este votante ya es jurado en otra mesa' : 'Disponible para asignación'}</span>
          </div>
          <button class="btn-primary" ${assigned ? 'disabled' : ''} onclick="asignarJuradoManual('${escapeHtml(v.identificacion)}', ${JuradosState.mesaManualActual}, '${escapeHtml($('manualCargo').value)}')">Asignar</button>
        </div>
      `;
    }).join('') || '<span class="badge-warning">Sin resultados.</span>';
  }

  async function asignarJuradoManual(identificacion, idMesa, cargo) {
    try {
      await API.post('/api/jurados/asignar', { identificacion, idMesa, cargo, fechaAsignacion: $('manualFecha').value });
      cerrarModal('modalAsignar');
      await cargarMesas();
      recalcularAsignacion();
      mostrarNotificacion('Jurado asignado');
    } catch (error) {
      mostrarNotificacion(error.message, 'warning');
    }
  }

  function removerJurado(identificacion, idMesa) {
    confirmar(`¿Remover a ${identificacion} de la Mesa ${idMesa}? El votante volverá a su puesto habitual.`, async () => {
      try {
        await API.request(`/api/jurados/${encodeURIComponent(identificacion)}/${idMesa}`, { method: 'DELETE' });
        await cargarMesas();
        recalcularAsignacion();
        mostrarNotificacion('Jurado removido');
      } catch (error) {
        mostrarNotificacion(error.message, 'error');
      }
    });
  }

  function renderPlanillaFiltros() {
    if (!$('planillaPuesto') || !$('planillaMesa')) return;
    const puesto = $('planillaPuesto').value;
    const mesas = JuradosState.mesas.filter((m) => !puesto || String(m.idPuesto) === String(puesto));
    $('planillaMesa').innerHTML = '<option value="">Todas las mesas</option>' + mesas.map((m) => `<option value="${m.idMesa}">Mesa ${m.idMesa}</option>`).join('');
  }

  function juradosFiltradosPlanilla() {
    const puesto = $('planillaPuesto').value;
    const mesaId = $('planillaMesa').value;
    return JuradosState.mesas
      .filter((m) => !puesto || String(m.idPuesto) === String(puesto))
      .filter((m) => !mesaId || String(m.idMesa) === String(mesaId))
      .flatMap((m) => (m.jurados || []).map((j) => ({ ...j, mesa: m })));
  }

  function generarPreviewPlanilla() {
    if (!$('planillaPreview')) return;
    const rows = juradosFiltradosPlanilla();
    $('planillaPreview').innerHTML = `
      <table>
        <thead><tr><th>Mesa</th><th>Jurado</th><th>Identificación</th><th>Cargo</th><th>Turno</th><th>Firma</th></tr></thead>
        <tbody>
          ${rows.map((j) => `<tr><td>${j.mesa.idMesa}</td><td>${escapeHtml(j.nombreCompleto)}</td><td>${escapeHtml(j.identificacion)}</td><td>${escapeHtml(j.cargo)}</td><td>${time(j.mesa.horaIngreso)} → ${time(j.mesa.horaSalida)}</td><td class="signature-cell"></td></tr>`).join('') || '<tr><td colspan="6">No hay jurados asignados para el filtro seleccionado.</td></tr>'}
        </tbody>
      </table>
    `;
  }

  async function exportarPlanillaPDF(idPuesto, idMesa) {
    const params = new URLSearchParams();
    if (idPuesto) params.set('puesto', idPuesto);
    if (idMesa) params.set('mesa', idMesa);
    const token = localStorage.getItem('abis_token');
    const response = await fetch(`/api/jurados/exportar/pdf?${params.toString()}`, {
      headers: token ? { Authorization: 'Bearer ' + token } : {}
    });
    if (!response.ok) throw new Error('No fue posible descargar la planilla');
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'planilla-jurados.pdf';
    link.click();
    URL.revokeObjectURL(url);
  }

  async function enviarCorreoJurado(jurado) {
    console.log('Pendiente envío:', jurado);
  }

  async function enviarCorreosJurados(jurados) {
    for (const item of jurados) await enviarCorreoJurado(item);
    mostrarNotificacion('Notificación por correo pendiente de implementación', 'warning');
  }

  function plantillaCorreoJurado(jurado) {
    return {
      nombre: jurado.nombreCompleto,
      mesa: jurado.idMesa,
      puesto: jurado.puestoAsignado,
      cargo: jurado.cargo,
      horario: `${jurado.horaIngreso} → ${jurado.horaSalida}`,
      instrucciones: 'Debe presentarse 30 minutos antes de su turno con documento de identidad.'
    };
  }

  function confirmar(message, callback) {
    $('confirmContent').innerHTML = `
      <h2>${escapeHtml(message)}</h2>
      <div class="modal-actions">
        <button class="btn-outline" onclick="cerrarModal('modalConfirm')">Cancelar</button>
        <button class="btn-primary" id="btnConfirmAction">Confirmar</button>
      </div>
    `;
    $('btnConfirmAction').addEventListener('click', async () => {
      cerrarModal('modalConfirm');
      await callback();
    }, { once: true });
    abrirModal('modalConfirm');
  }

  function abrirModal(id) {
    $(id)?.classList.add('open');
    $(id)?.setAttribute('aria-hidden', 'false');
  }

  function cerrarModal(id) {
    $(id)?.classList.remove('open');
    $(id)?.setAttribute('aria-hidden', 'true');
  }

  function mostrarNotificacion(mensaje, tipo = 'success') {
    const toast = document.createElement('div');
    toast.className = `jurados-toast ${tipo}`;
    toast.textContent = mensaje;
    $('juradosToasts').appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
  }

  function resetMetricas() {
    [
      'metricPool', 'metricJurados', 'metricMesas', 'metricCobertura',
      'summaryCoverage', 'summaryAssignable', 'summaryRequired',
      'summaryComplete', 'summaryIncomplete', 'summaryTotalJurados',
      'totalEstimado', 'distMesas3', 'distMesas2', 'footerPool', 'footerAsignar',
      'footerMesas', 'alertIncomplete', 'alertConflicts', 'legendEstudiantes',
      'legendDocentes', 'legendAdministrativos'
    ].forEach((id) => setText(id, '—'));
    setText('summaryCritical', '0 (0%)');
    setText('distMesas1', '0');
    setText('metricCoberturaUnit', '');
    setText('summaryCoverageUnit', '');
    setText('availabilityBadge', 'Sin datos aún');
    const bar = $('coverageBar');
    if (bar) {
      bar.style.setProperty('--target', '0%');
      bar.classList.remove('animate');
    }
    document.querySelectorAll('.donut-segment').forEach((circle) => {
      circle.style.strokeDasharray = '0 264';
    });
    if ($('btnEjecutarAsignacion')) $('btnEjecutarAsignacion').disabled = true;
  }

  function mostrarSkeletonMetricas(loading) {
    document.querySelectorAll('.metric-card, .summary-card, .distribution-table, .assignment-footer').forEach((element) => {
      element.classList.toggle('is-loading', loading);
      element.classList.toggle('is-updating', !loading && element.classList.contains('is-updating'));
    });
  }

  function animateCounter(id, target) {
    const element = $(id);
    if (!element) return;
    const duration = 600;
    const start = performance.now();
    const formatter = target > 999 ? number : (v) => String(v);
    function frame(now) {
      const progress = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - progress, 3);
      element.textContent = formatter(Math.round(target * eased));
      if (progress < 1) requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  }

  function animateDonut(total = 0) {
    const circumference = 2 * Math.PI * 42;
    const segments = [
      { selector: '.seg-1', start: 0, value: total ? 0.60 : 0 },
      { selector: '.seg-2', start: 0.60, value: total ? 0.25 : 0 },
      { selector: '.seg-3', start: 0.85, value: total ? 0.15 : 0 }
    ];
    segments.forEach((segment) => {
      const circle = document.querySelector(segment.selector);
      if (!circle) return;
      circle.style.strokeDasharray = `0 ${circumference}`;
      circle.style.strokeDashoffset = String(-circumference * segment.start);
      requestAnimationFrame(() => {
        circle.style.strokeDasharray = `${circumference * segment.value} ${circumference}`;
      });
    });
  }

  function animateDonutFromRoles(roles, pool) {
    const total = Number(pool || 0);
    const values = [
      total ? Number(roles.ESTUDIANTE || 0) / total : 0,
      total ? Number(roles.DOCENTE || 0) / total : 0,
      total ? Number(roles.ADMINISTRATIVO || 0) / total : 0
    ];
    const circumference = 2 * Math.PI * 42;
    let start = 0;
    ['.seg-1', '.seg-2', '.seg-3'].forEach((selector, index) => {
      const circle = document.querySelector(selector);
      if (!circle) return;
      circle.style.strokeDasharray = `0 ${circumference}`;
      circle.style.strokeDashoffset = String(-circumference * start);
      requestAnimationFrame(() => {
        circle.style.strokeDasharray = `${circumference * values[index]} ${circumference}`;
      });
      start += values[index];
    });
  }

  function setRoleLegend(id, value, total) {
    const percent = total ? Math.round((Number(value || 0) * 100) / total) : 0;
    setText(id, `${percent}% (${number(value)})`);
  }

  function poolCount() {
    return JuradosState.poolElegibles.length;
  }

  function mesasCount() {
    return JuradosState.mesas.length || JuradosState.puestos.length || 0;
  }

  function setText(id, value) {
    const element = $(id);
    if (element) element.textContent = value;
  }

  function debounce(fn, delay) {
    let timer;
    return (...args) => {
      clearTimeout(timer);
      timer = setTimeout(() => fn(...args), delay);
    };
  }

  function initials(value) {
    return String(value || 'JD').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p.charAt(0).toUpperCase()).join('') || 'JD';
  }

  function time(value) {
    if (!value) return '';
    const text = String(value);
    return text.includes('T') ? text.split('T')[1].slice(0, 5) : text.slice(0, 5);
  }

  window.JuradosState = JuradosState;
  window.initJurados = initJurados;
  window.switchTab = switchTab;
  window.calcularPool = calcularPool;
  window.calcularTotalEstimado = calcularTotalEstimado;
  window.simularAsignacion = simularAsignacion;
  window.ejecutarAsignacion = ejecutarAsignacion;
  window.enviarCorreoJurado = enviarCorreoJurado;
  window.plantillaCorreoJurado = plantillaCorreoJurado;
  window.cargarMesasPorPuesto = seleccionarPuesto;
  window.crearMesa = abrirModalMesa;
  window.editarMesa = editarMesa;
  window.eliminarMesa = eliminarMesa;
  window.buscarVotanteParaJurado = buscarVotanteParaJurado;
  window.asignarJuradoManual = asignarJuradoManual;
  window.removerJurado = removerJurado;
  window.generarPreviewPlanilla = generarPreviewPlanilla;
  window.exportarPlanillaPDF = exportarPlanillaPDF;
  window.mostrarNotificacion = mostrarNotificacion;
  window.agregarTurno = agregarTurno;
  window.seleccionarPuesto = seleccionarPuesto;
  window.abrirModalAsignarJurado = abrirModalAsignarJurado;
  window.cerrarModal = cerrarModal;

  initJurados();
})();
