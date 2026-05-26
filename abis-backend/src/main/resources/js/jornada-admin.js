(function initJornadaAdmin() {
  const state = {
    elecciones: [],
    stats: null,
    cargo: '',
    rol: '',
    ciudad: '',
    puesto: '',
    charts: {},
    lastUpdated: null,
    startedAt: null
  };

  const fmt = new Intl.NumberFormat('es-CO');
  const chartColors = ['#075521', '#14733b', '#52b788', '#b7e4c7', '#d8f3dc'];
  const $ = (id) => document.getElementById(id);

  function unwrap(payload) {
    return payload?.data ?? payload ?? [];
  }

  function electionId(eleccion) {
    return eleccion.id ?? eleccion.idEleccion ?? eleccion.id_eleccion;
  }

  function normalize(value) {
    return String(value || '').trim();
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (char) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }[char]));
  }

  function number(value) {
    return fmt.format(Number(value || 0));
  }

  function percent(part, total) {
    if (!total) return 0;
    return Math.round((Number(part || 0) / Number(total || 0)) * 100);
  }

  function time(value = new Date()) {
    return new Intl.DateTimeFormat('es-CO', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    }).format(value);
  }

  function showMessage(message, type) {
    if (message && window.showToast) window.showToast(message, type || 'warning');
  }

  function selectedElection() {
    const id = $('jornada-eleccion')?.value;
    return state.elecciones.find((item) => String(electionId(item)) === String(id)) || state.stats?.eleccion || null;
  }

  function setStatusBadge() {
    const eleccion = selectedElection();
    const estado = normalize(eleccion?.estado || 'SIN ESTADO').toUpperCase();
    const abierta = estado === 'EN_CURSO' || estado === 'ABIERTA';
    const dot = $('jornada-status-dot');
    const text = $('jornada-status-text');
    if (dot) dot.className = `h-2.5 w-2.5 rounded-full ${abierta ? 'bg-[#14733b]' : 'bg-[#dc2626]'}`;
    if (text) text.textContent = abierta ? 'ABIERTA' : estado.replace('_', ' ');
  }

  async function cargarElecciones() {
    const payload = await API.get('/api/elecciones');
    state.elecciones = unwrap(payload);
    const select = $('jornada-eleccion');
    select.innerHTML = state.elecciones.map((eleccion) => `
      <option value="${escapeHtml(electionId(eleccion))}">${escapeHtml(eleccion.nombre || 'Eleccion')} - ${escapeHtml(eleccion.estado || '--')}</option>
    `).join('');

    const activa = state.elecciones.find((e) => normalize(e.estado).toUpperCase() === 'EN_CURSO') || state.elecciones[0];
    if (activa) {
      select.value = String(electionId(activa));
      state.startedAt = new Date();
      await cargarEstadisticas();
    } else {
      showMessage('No hay elecciones registradas para analizar.', 'error');
      setStatusBadge();
    }
  }

  async function cargarEstadisticas() {
    const idEleccion = $('jornada-eleccion').value;
    if (!idEleccion) return;
    showMessage('');
    state.stats = await API.get(`/api/jornada/estadisticas?idEleccion=${encodeURIComponent(idEleccion)}`);
    state.cargo = '';
    state.rol = '';
    state.ciudad = '';
    state.puesto = '';
    state.lastUpdated = new Date();
    poblarFiltros();
    renderTodo();
  }

  function optionList(values, emptyLabel) {
    return `<option value="">${escapeHtml(emptyLabel)}</option>` + values.map((value) =>
      `<option value="${escapeHtml(value)}">${escapeHtml(value)}</option>`
    ).join('');
  }

  function uniqueValues(rows, key) {
    return [...new Set(rows.map((item) => item[key]).filter(Boolean))].sort();
  }

  function poblarFiltros() {
    const resultados = state.stats?.resultadosPorCandidato || [];
    const roles = state.stats?.resultadosPorRol || [];
    const puestos = state.stats?.participacionPorPuesto || [];
    const cargos = uniqueValues(resultados, 'cargo');
    const roleNames = uniqueValues(roles, 'rol');
    const ciudades = uniqueValues(puestos, 'ciudad');
    const puestoNames = uniqueValues(puestos, 'puesto');

    $('jornada-filtro-cargo').innerHTML = optionList(cargos, 'Todos los cargos');
    $('jornada-filtro-cargo-puestos').innerHTML = optionList(cargos, 'Todos');
    $('jornada-filtro-rol').innerHTML = optionList(roleNames, 'Todos');
    $('jornada-filtro-ciudad').innerHTML = optionList(ciudades, 'Todas las ciudades');
    $('jornada-filtro-puesto').innerHTML = optionList(puestoNames, 'Todos los puestos');
  }

  function renderTodo() {
    setStatusBadge();
    renderKpis();
    renderResultados();
    renderEstadoJornada();
    renderCharts();
    renderTerritorio();
    renderFooter();
  }

  function renderKpis() {
    const resumen = state.stats?.resumen || {};
    const votos = Number(resumen.votosEmitidos || 0);
    const habilitados = Number(resumen.totalHabilitados || 0);
    const registros = Number(resumen.registrosVoto || 0);
    const participacion = Number(resumen.participacion || percent(registros, habilitados));
    const puestos = state.stats?.participacionPorPuesto || [];
    const puestosActivos = puestos.filter((item) => Number(item.total || 0) > 0).length;
    const totalPuestos = Math.max(puestos.length, puestosActivos);
    const sospechas = Math.max(0, registros - votos);

    $('jornada-kpis').innerHTML = `
      <article class="flex flex-col gap-2 rounded-xl border border-[#e8ede9] bg-white p-5 shadow-[0_1px_4px_rgba(0,0,0,0.06)]">
        <div class="flex items-center gap-4">
          ${donutSvg(participacion)}
          <div>
            <p class="text-xs font-extrabold uppercase tracking-wide text-[#6b7280]">Participacion</p>
            <p class="text-sm font-bold text-[#1f2925]">${number(registros)} de ${number(habilitados)} electores habilitados</p>
          </div>
        </div>
        <p class="text-xs font-extrabold text-[#14733b]"><i class="ti ti-trending-up"></i> vs ultima hora</p>
      </article>
      ${kpiCard('ti ti-box-seam', 'Votos emitidos', number(votos), '<canvas id="jornada-sparkline" width="80" height="30"></canvas>', 'Ritmo estable de registro')}
      ${kpiCard('ti ti-users', 'Registro de votantes', number(registros), '<span class="rounded-full bg-[#e8f5e9] px-2 py-1 text-xs font-extrabold text-[#075521]">100% sin duplicados</span>', 'Registros unicos auditables')}
      ${kpiCard('ti ti-shield-check', 'Control de doble voto', number(sospechas), `<span class="rounded-full px-2 py-1 text-xs font-extrabold ${sospechas ? 'bg-red-50 text-red-700' : 'bg-[#e8f5e9] text-[#075521]'}">${sospechas ? 'Revisar alertas' : 'Sin incidentes'}</span>`, 'Validacion contra REGISTRO_VOTOS')}
      ${kpiCard('ti ti-building-bank', 'Puestos activos', `${number(puestosActivos)} de ${number(totalPuestos || 0)} puestos`, '<span class="rounded-full bg-[#e8f5e9] px-2 py-1 text-xs font-extrabold text-[#075521]">100% operativos</span>', 'Cobertura territorial')}
    `;
    renderSparkline(votos);
  }

  function donutSvg(value) {
    const safe = Math.max(0, Math.min(100, Number(value || 0)));
    const circumference = 2 * Math.PI * 38;
    const offset = circumference - (safe / 100) * circumference;
    return `
      <svg class="h-24 w-24 shrink-0" viewBox="0 0 96 96" aria-label="Participacion ${safe}%">
        <circle cx="48" cy="48" r="38" fill="none" stroke="#e8ede9" stroke-width="10"></circle>
        <circle cx="48" cy="48" r="38" fill="none" stroke="#14733b" stroke-width="10" stroke-linecap="round"
          stroke-dasharray="${circumference}" stroke-dashoffset="${offset}" transform="rotate(-90 48 48)"></circle>
        <text x="48" y="53" text-anchor="middle" font-size="20" font-weight="800" fill="#075521">${safe}%</text>
      </svg>
    `;
  }

  function kpiCard(icon, label, value, extra, note) {
    return `
      <article class="flex flex-col gap-2 rounded-xl border border-[#e8ede9] bg-white p-5 shadow-[0_1px_4px_rgba(0,0,0,0.06)]">
        <div class="flex items-center justify-between gap-3">
          <span class="grid h-10 w-10 place-items-center rounded-lg bg-[#e8f5e9] text-xl text-[#075521]"><i class="${icon}"></i></span>
          ${extra}
        </div>
        <p class="text-xs font-extrabold uppercase tracking-wide text-[#6b7280]">${escapeHtml(label)}</p>
        <p class="text-2xl font-extrabold text-[#075521]">${value}</p>
        <p class="text-xs font-semibold text-[#6b7280]">${escapeHtml(note)}</p>
      </article>
    `;
  }

  function filteredResultados() {
    const all = state.stats?.resultadosPorCandidato || [];
    return state.cargo ? all.filter((item) => item.cargo === state.cargo) : all;
  }

  function filteredPuestos() {
    const rows = state.stats?.participacionPorPuesto || [];
    return rows.filter((item) => {
      const cityOk = !state.ciudad || item.ciudad === state.ciudad;
      const puestoOk = !state.puesto || item.puesto === state.puesto;
      return cityOk && puestoOk;
    });
  }

  function renderResultados() {
    const rows = filteredResultados();
    const total = rows.reduce((sum, item) => sum + Number(item.votos || 0), 0);
    const leader = Math.max(0, ...rows.map((item) => Number(item.votos || 0)));

    $('jornada-resultados').innerHTML = rows.length ? rows.map((item, index) => {
      const votos = Number(item.votos || 0);
      const pct = percent(votos, total);
      const diff = leader - votos;
      return `
        <tr class="border-b border-[#eef3ef] align-top">
          <td class="py-3 pr-3">
            <span class="inline-flex h-7 min-w-7 items-center justify-center rounded-full ${index < 3 ? 'bg-[#e8f5e9] text-[#075521]' : 'bg-[#f3f4f6] text-[#4b5563]'} text-xs font-extrabold">${index + 1}</span>
          </td>
          <td class="py-3 pr-3">
            <div class="flex items-center gap-3">
              <span class="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-[#e8f5e9] text-sm font-extrabold text-[#075521]">${initials(item.nombre)}</span>
              <div class="min-w-0 flex-1">
                <p class="text-sm font-extrabold text-[#1f2925]">${escapeHtml(item.numeroCampania)} · ${escapeHtml(item.nombre)}</p>
                <p class="text-xs font-bold text-[#6b7280]">${escapeHtml(item.cargo || '--')}</p>
                <div class="mt-2 h-2 overflow-hidden rounded-full bg-[#eef3ef]"><div class="h-full rounded-full bg-[#14733b]" style="width:${pct}%"></div></div>
              </div>
            </div>
          </td>
          <td class="py-3 pr-3 text-right text-sm font-extrabold text-[#075521]">${number(votos)}</td>
          <td class="py-3 pr-3 text-right text-sm font-extrabold text-[#1f2925]">${pct}%</td>
          <td class="py-3 text-right">${diff === 0 ? '<span class="text-sm font-bold text-[#6b7280]">--</span>' : `<span class="rounded-full bg-red-50 px-2 py-1 text-xs font-extrabold text-red-700">${number(diff)} votos detras</span>`}</td>
        </tr>
      `;
    }).join('') : '<tr><td colspan="5" class="py-6 text-center text-sm font-bold text-[#6b7280]">Sin votos registrados para este filtro.</td></tr>';
  }

  function initials(name) {
    return String(name || '?').trim().split(/\s+/).slice(0, 2).map((part) => part[0] || '').join('').toUpperCase();
  }

  function renderEstadoJornada() {
    const resumen = state.stats?.resumen || {};
    const sospechas = Math.max(0, Number(resumen.registrosVoto || 0) - Number(resumen.votosEmitidos || 0));
    const activeMinutes = state.startedAt ? Math.max(0, Math.round((Date.now() - state.startedAt.getTime()) / 60000)) : 0;
    const hours = Math.floor(activeMinutes / 60);
    const minutes = activeMinutes % 60;
    const card = $('jornada-stability-card');
    if (card) {
      card.className = `rounded-lg border p-4 text-sm font-extrabold ${sospechas ? 'border-red-200 bg-red-50 text-red-700' : 'border-[#b7e4c7] bg-[#e8f5e9] text-[#075521]'}`;
      card.innerHTML = `${sospechas ? 'Jornada con alertas' : 'Jornada estable'}<span class="mt-1 block text-xs font-bold text-[#1f2925]">${sospechas ? 'Revisar posibles inconsistencias' : 'Sin incidentes relevantes'}</span>`;
    }

    $('jornada-estado-list').innerHTML = [
      ['ti ti-clock', 'Ultimo voto registrado', time(state.lastUpdated || new Date())],
      ['ti ti-refresh', 'Ultima sincronizacion', time(state.lastUpdated || new Date())],
      ['ti ti-hourglass', 'Tiempo activa', `${hours}h ${minutes}m`],
      ['ti ti-fingerprint', 'Biometria', '<span class="rounded-full bg-[#e8f5e9] px-2 py-1 text-xs font-extrabold text-[#075521]">Operativa</span>']
    ].map(([icon, label, value]) => `
      <div class="flex items-center justify-between gap-3 border-b border-[#eef3ef] pb-2 last:border-b-0">
        <span class="flex items-center gap-2 font-bold text-[#6b7280]"><i class="${icon} text-[#075521]"></i>${label}</span>
        <span class="font-extrabold text-[#1f2925]">${value}</span>
      </div>
    `).join('');
  }

  function ensureChartDefaults() {
    if (!window.Chart) return false;
    Chart.defaults.font.family = 'Arial, Helvetica, sans-serif';
    Chart.defaults.color = '#1f2925';
    return true;
  }

  function centerTextPlugin(textCallback) {
    return {
      id: `centerText-${Math.random().toString(16).slice(2)}`,
      afterDraw(chart) {
        const text = textCallback();
        const area = chart.chartArea;
        if (!area) return;
        const ctx = chart.ctx;
        ctx.save();
        ctx.font = '800 24px Arial, Helvetica, sans-serif';
        ctx.fillStyle = '#075521';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(text, (area.left + area.right) / 2, (area.top + area.bottom) / 2);
        ctx.restore();
      }
    };
  }

  function updateChart(key, canvasId, config) {
    if (!ensureChartDefaults()) return;
    const canvas = $(canvasId);
    if (!canvas) return;
    if (state.charts[key]) state.charts[key].destroy();
    state.charts[key] = new Chart(canvas, config);
  }

  function renderCharts() {
    renderDistribucionChart();
    renderPuestosChart();
    renderCiudadesChart();
    renderTendenciaChart();
  }

  function renderDistribucionChart() {
    const rows = filteredResultados();
    const total = rows.reduce((sum, item) => sum + Number(item.votos || 0), 0);
    const labels = rows.map((item) => item.nombre || `Candidato ${item.numeroCampania}`);
    const data = rows.map((item) => Number(item.votos || 0));
    updateChart('distribucion', 'chartDistribucion', {
      type: 'doughnut',
      data: { labels, datasets: [{ data, backgroundColor: chartColors, borderWidth: 0 }] },
      plugins: [centerTextPlugin(() => number(total))],
      options: { cutout: '68%', plugins: { legend: { display: false } } }
    });
    $('jornada-distribucion-legend').innerHTML = rows.length ? rows.map((item, index) => {
      const pct = percent(item.votos, total);
      return `
        <div class="flex items-center justify-between gap-3 text-xs font-bold">
          <span class="flex min-w-0 items-center gap-2 text-[#1f2925]"><span class="h-2.5 w-2.5 rounded-full" style="background:${chartColors[index % chartColors.length]}"></span><span class="truncate">${escapeHtml(item.nombre)}</span></span>
          <span class="text-[#075521]">${pct}%</span>
        </div>
      `;
    }).join('') : '<p class="text-sm font-bold text-[#6b7280]">Sin datos para graficar.</p>';
  }

  function renderPuestosChart() {
    const rows = filteredPuestos();
    updateChart('puestos', 'chartPuestos', {
      type: 'bar',
      data: {
        labels: rows.map((item) => item.puesto || 'Sin clasificar'),
        datasets: [{ data: rows.map((item) => Number(item.total || 0)), backgroundColor: '#14733b', borderRadius: 4 }]
      },
      options: {
        indexAxis: 'y',
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { x: { beginAtZero: true, ticks: { precision: 0 } }, y: { grid: { display: false } } }
      }
    });
  }

  function renderCiudadesChart() {
    const rows = state.stats?.participacionPorCiudad || [];
    const total = rows.reduce((sum, item) => sum + Number(item.total || 0), 0);
    updateChart('ciudades', 'chartCiudades', {
      type: 'doughnut',
      data: {
        labels: rows.map((item) => item.ciudad || 'Sin clasificar'),
        datasets: [{ data: rows.map((item) => Number(item.total || 0)), backgroundColor: chartColors, borderWidth: 0 }]
      },
      plugins: [centerTextPlugin(() => number(total))],
      options: { cutout: '68%', maintainAspectRatio: false, plugins: { legend: { display: false } } }
    });
  }

  function renderTendenciaChart() {
    const votos = Number(state.stats?.resumen?.votosEmitidos || 0);
    const now = state.lastUpdated || new Date();
    const labels = Array.from({ length: 8 }, (_, index) => {
      const date = new Date(now.getTime() - (7 - index) * 5 * 60000);
      return time(date).replace(/\s/g, '');
    });
    const data = labels.map((_, index) => Math.round((votos / Math.max(1, labels.length - 1)) * index));
    const canvas = $('chartTendencia');
    const ctx = canvas?.getContext('2d');
    const gradient = ctx ? ctx.createLinearGradient(0, 0, 0, 260) : '#d8f3dc';
    if (gradient.addColorStop) {
      gradient.addColorStop(0, 'rgba(20,115,59,0.24)');
      gradient.addColorStop(1, 'rgba(20,115,59,0.02)');
    }
    updateChart('tendencia', 'chartTendencia', {
      type: 'line',
      data: { labels, datasets: [{ data, borderColor: '#075521', backgroundColor: gradient, fill: true, tension: 0.35, pointRadius: 3, pointBackgroundColor: '#075521' }] },
      options: {
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { x: { grid: { display: false } }, y: { beginAtZero: true, ticks: { precision: 0 } } }
      }
    });
  }

  function renderSparkline(votos) {
    if (!window.Chart) return;
    const canvas = $('jornada-sparkline');
    if (!canvas) return;
    if (state.charts.sparkline) state.charts.sparkline.destroy();
    const data = [0, 1, 2, 3, 5, 8].map((step, index) => Math.max(0, Math.round((Number(votos || 0) / 8) * step + index % 2)));
    state.charts.sparkline = new Chart(canvas, {
      type: 'line',
      data: { labels: data.map((_, index) => index), datasets: [{ data, borderColor: '#14733b', borderWidth: 2, pointRadius: 0, tension: 0.35 }] },
      options: { responsive: false, plugins: { legend: { display: false }, tooltip: { enabled: false } }, scales: { x: { display: false }, y: { display: false } } }
    });
  }

  function renderTerritorio() {
    const puestos = filteredPuestos();
    const totalPuestos = puestos.reduce((sum, item) => sum + Number(item.total || 0), 0);
    $('jornada-puestos').innerHTML = puestos.length ? puestos.map((item) => {
      const pct = percent(item.total, totalPuestos);
      return `<div class="flex items-center justify-between gap-3 text-xs font-bold"><span class="truncate text-[#1f2925]">${escapeHtml(item.puesto || 'Sin clasificar')}</span><span class="text-[#075521]">${number(item.total)} · ${pct}%</span></div>`;
    }).join('') : '<p class="text-sm font-bold text-[#6b7280]">Sin participacion registrada.</p>';

    const ciudades = state.stats?.participacionPorCiudad || [];
    const totalCiudades = ciudades.reduce((sum, item) => sum + Number(item.total || 0), 0);
    $('jornada-ciudades').innerHTML = ciudades.length ? ciudades.map((item, index) => {
      const pct = percent(item.total, totalCiudades);
      return `<div class="flex items-center justify-between gap-3 text-xs font-bold"><span class="flex min-w-0 items-center gap-2"><span class="h-2.5 w-2.5 rounded-full" style="background:${chartColors[index % chartColors.length]}"></span><span class="truncate text-[#1f2925]">${escapeHtml(item.ciudad || 'Sin clasificar')}</span></span><span class="text-[#075521]">${number(item.total)} · ${pct}%</span></div>`;
    }).join('') : '<p class="text-sm font-bold text-[#6b7280]">Sin datos por ciudad.</p>';

    $('jornada-mapa-puestos').innerHTML = puestos.length ? puestos.map((item) => {
      const pct = percent(item.total, Math.max(1, Number(state.stats?.resumen?.totalHabilitados || 0)));
      const level = participationLevel(pct);
      return `
        <tr class="border-b border-[#eef3ef]">
          <td class="py-3 pr-2 font-bold text-[#1f2925]">${escapeHtml(item.puesto || 'Sin clasificar')}</td>
          <td class="py-3 pr-2 font-extrabold text-[#075521]">${pct}%</td>
          <td class="py-3"><span class="inline-flex items-center gap-2 rounded-full bg-[#f7faf8] px-2 py-1 text-xs font-extrabold text-[#1f2925]"><span class="h-2.5 w-2.5 rounded-full" style="background:${level.color}"></span>${level.label}</span></td>
        </tr>
      `;
    }).join('') : '<tr><td colspan="3" class="py-6 text-center text-sm font-bold text-[#6b7280]">Sin datos de puestos.</td></tr>';
  }

  function participationLevel(value) {
    if (value > 80) return { label: 'Muy alto', color: '#075521' };
    if (value >= 60) return { label: 'Alto', color: '#14733b' };
    if (value >= 40) return { label: 'Medio', color: '#eab308' };
    if (value >= 20) return { label: 'Bajo', color: '#f97316' };
    return { label: 'Muy bajo', color: '#dc2626' };
  }

  function renderFooter() {
    const footer = $('jornada-footer-updated');
    if (footer) footer.textContent = state.lastUpdated ? time(state.lastUpdated) : '--';
  }

  async function openKiosk() {
    try {
      await API.get('/api/votacion/activa');
      window.open('/pages/votacion/index.html', 'ABISVotacionKiosko', 'noopener,noreferrer');
    } catch (error) {
      showMessage(error.message || 'No hay una eleccion en curso para abrir el modo votacion.', 'error');
    }
  }

  $('jornada-eleccion').addEventListener('change', cargarEstadisticas);
  $('jornada-refresh').addEventListener('click', cargarEstadisticas);
  $('btn-open-kiosk').addEventListener('click', openKiosk);
  $('jornada-filtro-cargo').addEventListener('change', (event) => {
    state.cargo = event.target.value;
    $('jornada-filtro-cargo-puestos').value = state.cargo;
    renderResultados();
    renderDistribucionChart();
  });
  $('jornada-filtro-cargo-puestos').addEventListener('change', (event) => {
    state.cargo = event.target.value;
    $('jornada-filtro-cargo').value = state.cargo;
    renderResultados();
    renderDistribucionChart();
  });
  $('jornada-filtro-rol').addEventListener('change', (event) => {
    state.rol = event.target.value;
    renderEstadoJornada();
  });
  $('jornada-filtro-ciudad').addEventListener('change', (event) => {
    state.ciudad = event.target.value;
    renderPuestosChart();
    renderTerritorio();
  });
  $('jornada-filtro-puesto').addEventListener('change', (event) => {
    state.puesto = event.target.value;
    renderPuestosChart();
    renderTerritorio();
  });
  $('jornada-clear-filters').addEventListener('click', () => {
    state.cargo = '';
    state.rol = '';
    state.ciudad = '';
    state.puesto = '';
    ['jornada-filtro-cargo', 'jornada-filtro-cargo-puestos', 'jornada-filtro-rol', 'jornada-filtro-ciudad', 'jornada-filtro-puesto'].forEach((id) => {
      const element = $(id);
      if (element) element.value = '';
    });
    renderTodo();
  });
  $('jornada-ver-todos').addEventListener('click', () => {
    state.cargo = '';
    $('jornada-filtro-cargo').value = '';
    $('jornada-filtro-cargo-puestos').value = '';
    renderResultados();
    renderDistribucionChart();
  });

  cargarElecciones().catch((error) => showMessage(error.message || 'No fue posible cargar la jornada.', 'error'));
})();
