(function () {
  const state = {
    elecciones: [],
    filtradas: [],
    pagina: 1,
    porPagina: 5,
    countdown: null,
    editando: null
  };

  const fmt = new Intl.NumberFormat('es-CO');
  const token = () => localStorage.getItem('abis_token') || '';
  const authHeaders = () => token() ? { Authorization: `Bearer ${token()}` } : {};

  function normalizarLista(payload) {
    if (Array.isArray(payload)) return payload;
    if (Array.isArray(payload?.data)) return payload.data;
    return [];
  }

  function normalizarEstado(estado) {
    const value = String(estado || '').toUpperCase().replace(/\s+/g, '_');
    if (value === 'CERRADA') return 'FINALIZADA';
    return value || 'PROGRAMADA';
  }

  function estadoMeta(estado) {
    const value = normalizarEstado(estado);
    const map = {
      PROGRAMADA: { label: 'Programada', cls: 'programada', icon: 'calendar_month' },
      EN_CURSO: { label: 'En curso', cls: 'en-curso', icon: 'play_arrow' },
      FINALIZADA: { label: 'Finalizada', cls: 'finalizada', icon: 'task_alt' },
      CANCELADA: { label: 'Cancelada', cls: 'cancelada', icon: 'cancel' }
    };
    return map[value] || map.PROGRAMADA;
  }

  function fechaValor(value) {
    return value ? new Date(value) : null;
  }

  function fechaCorta(value) {
    const date = fechaValor(value);
    if (!date || Number.isNaN(date.getTime())) return ['--', '--'];
    return [
      date.toLocaleDateString('es-CO', { day: '2-digit', month: '2-digit', year: 'numeric' }),
      date.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit', hour12: true })
    ];
  }

  function fechaLarga(value) {
    const date = fechaValor(value);
    if (!date || Number.isNaN(date.getTime())) return 'Fecha pendiente';
    return date.toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' });
  }

  function duracionHoras(inicio, fin) {
    const start = fechaValor(inicio);
    const end = fechaValor(fin);
    if (!start || !end) return '--';
    const horas = Math.max(0, Math.round((end - start) / 3600000));
    return `${horas} horas`;
  }

  function pct(part, total) {
    if (!total) return 0;
    return Math.max(0, Math.min(100, Math.round((part / total) * 100)));
  }

  function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
  }

  function mostrarSkeleton() {
    const tbody = document.getElementById('elecciones-tbody');
    if (!tbody) return;
    tbody.innerHTML = Array.from({ length: 5 }).map(() => `
      <tr>
        <td colspan="8"><div class="dashboard-skeleton h-[46px]"></div></td>
      </tr>
    `).join('');
  }

  async function requestJson(url, options = {}) {
    const response = await fetch(url, {
      headers: {
        'Content-Type': 'application/json',
        ...authHeaders(),
        ...(options.headers || {})
      },
      ...options
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || data.error || 'Error de comunicación');
    return data;
  }

  async function loadElecciones() {
    mostrarSkeleton();
    try {
      const payload = await requestJson('/api/elecciones');
      state.elecciones = normalizarLista(payload);
      state.filtradas = state.elecciones;
      state.pagina = 1;
      renderEleccionesTable();
      renderNextElection();
      renderTimeline();
    } catch (error) {
      document.getElementById('elecciones-tbody').innerHTML = `
        <tr><td colspan="8" class="text-center"><p class="dashboard-error">Error al cargar procesos electorales</p></td></tr>
      `;
      showToast('Error al cargar elecciones', 'error');
    }
  }

  async function loadStats() {
    try {
      const stats = await requestJson('/api/elecciones/stats');
      const total = Number(stats.total || 0);
      setText('total-procesos', total);
      setText('programadas', stats.programadas || 0);
      setText('en-curso', stats.enCurso || 0);
      setText('finalizadas', stats.finalizadas || 0);
      setText('hero-electores', fmt.format(Number(stats.electoresHabilitados || 0)));
      setText('hero-proximas', stats.proximas || 0);
      setText('programadas-pct', `${pct(Number(stats.programadas || 0), total)}% del total`);
      setText('en-curso-pct', `${pct(Number(stats.enCurso || 0), total)}% del total`);
      setText('finalizadas-pct', `${pct(Number(stats.finalizadas || 0), total)}% del total`);
    } catch (error) {
      ['total-procesos', 'programadas', 'en-curso', 'finalizadas', 'hero-electores', 'hero-proximas'].forEach((id) => setText(id, '--'));
    }
  }

  function renderEleccionesTable() {
    const tbody = document.getElementById('elecciones-tbody');
    if (!tbody) return;
    if (!state.filtradas.length) {
      tbody.innerHTML = '<tr><td colspan="8" class="text-center"><p class="dashboard-empty">Sin procesos electorales registrados</p></td></tr>';
      renderPagination();
      return;
    }

    const start = (state.pagina - 1) * state.porPagina;
    const rows = state.filtradas.slice(start, start + state.porPagina);
    tbody.innerHTML = rows.map((eleccion) => renderRow(eleccion)).join('');
    aplicarBarrasParticipacion();
    renderPagination();
  }

  function renderRow(eleccion) {
    const meta = estadoMeta(eleccion.estado);
    const estado = normalizarEstado(eleccion.estado);
    const [fechaInicio, horaInicio] = fechaCorta(eleccion.fechaHoraInicio);
    const [fechaFin, horaFin] = fechaCorta(eleccion.fechaHoraFin);
    const participacion = Number(eleccion.participacion || 0);
    const candidatos = Number(eleccion.candidatos || 0);
    return `
      <tr>
        <td>
          <button type="button" class="election-name-cell" onclick="abrirDetalleEleccion(${eleccion.id})">
            <span class="election-row-icon status-badge-${meta.cls}"><span class="material-symbols-outlined text-[18px]">${meta.icon}</span></span>
            <span class="election-row-title">${escapeHtml(eleccion.nombre || '--')}</span>
          </button>
        </td>
        <td><p class="date-primary">${fechaInicio}</p><p class="date-muted">${horaInicio}</p></td>
        <td><p class="date-primary">${fechaFin}</p><p class="date-muted">${horaFin}</p></td>
        <td class="text-center">${duracionHoras(eleccion.fechaHoraInicio, eleccion.fechaHoraFin)}</td>
        <td>${statusBadge(eleccion.estado)}</td>
        <td>${participationMarkup(estado, participacion)}</td>
        <td class="text-center font-bold">${candidatos || '—'}</td>
        <td><div class="row-actions">${actionButtons(eleccion, estado)}</div></td>
      </tr>
    `;
  }

  function statusBadge(estado) {
    const meta = estadoMeta(estado);
    return `<span class="status-badge status-badge-${meta.cls}"><span class="status-badge-dot"></span>${meta.label}</span>`;
  }

  function participationMarkup(estado, value) {
    if (estado === 'PROGRAMADA') return '<span class="text-[#667085]">—</span>';
    if (estado === 'CANCELADA') return '<span class="text-[#991B1B]">—</span>';
    const fillClass = estado === 'FINALIZADA' ? 'participation-fill participation-fill-blue' : 'participation-fill';
    return `
      <div class="participation-cell">
        <p class="participation-value">${value}%</p>
        <div class="participation-track"><div class="${fillClass}" data-progress="${value}"></div></div>
      </div>
    `;
  }

  function aplicarBarrasParticipacion() {
    document.querySelectorAll('[data-progress]').forEach((bar) => {
      bar.style.width = `${bar.dataset.progress}%`;
    });
  }

  function actionButtons(eleccion, estado) {
    const id = eleccion.id;
    const menu = `<button type="button" class="action-menu" onclick="toggleMenuEleccion(event, ${id})"><span class="material-symbols-outlined">more_horiz</span></button>`;
    if (estado === 'PROGRAMADA') {
      return `<button type="button" class="action-primary" onclick="iniciarEleccion(${id})">Iniciar</button>
        <button type="button" class="action-ghost" onclick="editarEleccion(${id})">Editar</button>${menu}`;
    }
    if (estado === 'EN_CURSO') {
      return `<button type="button" class="action-ghost" onclick="editarEleccion(${id})">Editar</button>
        <button type="button" class="action-ghost" onclick="verCandidatos(${id})">Candidatos</button>${menu}`;
    }
    if (estado === 'FINALIZADA') {
      return `<button type="button" class="action-ghost" onclick="verResumen(${id})">Ver resumen</button>${menu}`;
    }
    return `<button type="button" class="action-ghost" onclick="abrirDetalleEleccion(${id})">Ver detalle</button>${menu}`;
  }

  function renderPagination() {
    const total = state.filtradas.length;
    const pages = Math.max(1, Math.ceil(total / state.porPagina));
    const start = total ? ((state.pagina - 1) * state.porPagina) + 1 : 0;
    const end = Math.min(total, state.pagina * state.porPagina);
    setText('pagination-summary', `Mostrando ${start} a ${end} de ${total} procesos`);
    const controls = document.getElementById('pagination-controls');
    if (!controls) return;
    controls.innerHTML = `
      <button type="button" class="pagination-button" onclick="cambiarPagina(${Math.max(1, state.pagina - 1)})"><span class="material-symbols-outlined text-[18px]">chevron_left</span></button>
      ${Array.from({ length: pages }).map((_, i) => `<button type="button" class="pagination-button ${state.pagina === i + 1 ? 'active' : ''}" onclick="cambiarPagina(${i + 1})">${i + 1}</button>`).join('')}
      <button type="button" class="pagination-button" onclick="cambiarPagina(${Math.min(pages, state.pagina + 1)})"><span class="material-symbols-outlined text-[18px]">chevron_right</span></button>
    `;
  }

  function renderNextElection() {
    const next = state.elecciones
      .filter((e) => normalizarEstado(e.estado) === 'PROGRAMADA')
      .sort((a, b) => new Date(a.fechaHoraInicio) - new Date(b.fechaHoraInicio))[0];
    if (!next) {
      setText('next-election-name', 'Sin jornada programada');
      setText('next-election-date', 'Fecha pendiente');
      startCountdown(null);
      renderPreparation(null);
      return;
    }
    setText('next-election-name', next.nombre || 'Elección programada');
    setText('next-election-date', fechaLarga(next.fechaHoraInicio));
    document.getElementById('next-election-status').outerHTML = statusBadge(next.estado).replace('status-badge ', 'status-badge mt-2 ');
    startCountdown(next.fechaHoraInicio);
    renderPreparation(next.id);
  }

  async function renderPreparation(id) {
    const defaults = {
      definirCandidatos: false,
      asignarJurados: true,
      configurarPuestos: true,
      publicacionOficial: false,
      porcentaje: 50
    };
    let data = defaults;
    if (id) {
      try {
        data = await requestJson(`/api/elecciones/preparacion/${id}`);
      } catch (error) {
        data = defaults;
      }
    }
    const items = [
      ['definirCandidatos', 'Definir candidatos'],
      ['asignarJurados', 'Asignar jurados'],
      ['configurarPuestos', 'Configurar puestos'],
      ['publicacionOficial', 'Publicación oficial']
    ];
    const completed = items.filter(([key]) => Boolean(data[key])).length;
    const percent = Number(data.porcentaje ?? completed * 25);
    setText('prep-percent', `${percent}%`);
    document.getElementById('prep-fill').style.width = `${percent}%`;
    document.getElementById('prep-list').innerHTML = items.map(([key, label]) => {
      const done = Boolean(data[key]);
      return `
        <div class="prep-item">
          <span class="prep-check ${done ? 'done' : ''}"><span class="material-symbols-outlined text-[13px]">check</span></span>
          <span class="prep-label">${label}</span>
          <span class="prep-status ${done ? '' : 'pending'}">${done ? 'Completado' : 'Pendiente'}</span>
          <span class="prep-check ${done ? 'done' : ''}"><span class="material-symbols-outlined text-[13px]">check</span></span>
        </div>
      `;
    }).join('');
  }

  function renderTimeline() {
    const current = state.elecciones.some((e) => normalizarEstado(e.estado) === 'EN_CURSO') ? 4 : 3;
    const phases = ['Planeación', 'Registro', 'Validación', 'Activación', 'Votación', 'Cierre'];
    const titleYear = state.elecciones[0]?.fechaHoraInicio ? new Date(state.elecciones[0].fechaHoraInicio).getFullYear() : new Date().getFullYear();
    setText('timeline-title', `Cronograma Electoral ${titleYear}`);
    document.getElementById('timeline-steps').innerHTML = phases.map((phase, index) => {
      const cls = index < current ? 'done' : index === current ? 'current' : '';
      return `<div class="timeline-step ${cls}">${phase}<span class="timeline-dot">${index < current ? '<span class="material-symbols-outlined text-[14px]">check</span>' : ''}</span></div>`;
    }).join('');
    setText('timeline-phase', phases[current]);
    setText('timeline-count', `${current}/6`);
  }

  function startCountdown(targetDate) {
    if (state.countdown) clearInterval(state.countdown);
    const tick = () => {
      const diff = targetDate ? Math.max(0, new Date(targetDate) - new Date()) : 0;
      const days = Math.floor(diff / 86400000);
      const hours = Math.floor((diff % 86400000) / 3600000);
      const mins = Math.floor((diff % 3600000) / 60000);
      const secs = Math.floor((diff % 60000) / 1000);
      setText('cd-dias', String(days).padStart(2, '0'));
      setText('cd-horas', String(hours).padStart(2, '0'));
      setText('cd-mins', String(mins).padStart(2, '0'));
      setText('cd-segs', String(secs).padStart(2, '0'));
    };
    tick();
    state.countdown = setInterval(tick, 1000);
  }

  window.cambiarPagina = function cambiarPagina(page) {
    state.pagina = page;
    renderEleccionesTable();
  };

  window.abrirModalNuevaEleccion = function abrirModalNuevaEleccion() {
    state.editando = null;
    document.querySelector('.modal-title').textContent = 'Nueva Elección';
    document.getElementById('form-eleccion').reset();
    document.getElementById('peso-estudiante').value = '1.0';
    document.getElementById('peso-docente').value = '2.0';
    document.getElementById('peso-egresado').value = '1.0';
    document.getElementById('peso-administrativo').value = '1.5';
    ocultarErrorModal();
    document.getElementById('modal-eleccion').classList.add('open');
  };

  window.cerrarModalEleccion = function cerrarModalEleccion() {
    document.getElementById('modal-eleccion').classList.remove('open');
  };

  window.editarEleccion = function editarEleccion(id) {
    const election = state.elecciones.find((e) => Number(e.id) === Number(id));
    if (!election || normalizarEstado(election.estado) !== 'PROGRAMADA') {
      showToast('Solo se pueden editar elecciones programadas', 'warning');
      return;
    }
    state.editando = election;
    document.querySelector('.modal-title').textContent = 'Editar Elección';
    document.getElementById('eleccion-nombre').value = election.nombre || '';
    document.getElementById('eleccion-inicio').value = String(election.fechaHoraInicio || '').slice(0, 16);
    document.getElementById('eleccion-fin').value = String(election.fechaHoraFin || '').slice(0, 16);
    ocultarErrorModal();
    document.getElementById('modal-eleccion').classList.add('open');
  };

  document.getElementById('form-eleccion')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const body = {
      nombre: document.getElementById('eleccion-nombre').value.trim(),
      fechaHoraInicio: document.getElementById('eleccion-inicio').value,
      fechaHoraFin: document.getElementById('eleccion-fin').value,
      pesosRoles: {
        estudiante: Number(document.getElementById('peso-estudiante').value),
        docente: Number(document.getElementById('peso-docente').value),
        egresado: Number(document.getElementById('peso-egresado').value),
        administrativo: Number(document.getElementById('peso-administrativo').value)
      }
    };
    if (!body.nombre || !body.fechaHoraInicio || !body.fechaHoraFin) {
      mostrarErrorModal('Completa los campos obligatorios.');
      return;
    }
    try {
      if (state.editando) {
        await requestJson(`/api/elecciones/${state.editando.id}`, { method: 'PUT', body: JSON.stringify(body) });
        showToast('Elección actualizada', 'success');
      } else {
        await requestJson('/api/elecciones', { method: 'POST', body: JSON.stringify(body) });
        showToast('Elección creada', 'success');
      }
      cerrarModalEleccion();
      await Promise.all([loadElecciones(), loadStats()]);
    } catch (error) {
      mostrarErrorModal(error.message || 'No fue posible guardar la elección.');
    }
  });

  window.iniciarEleccion = async function iniciarEleccion(idEleccion) {
    if (!confirm('¿Está seguro de iniciar esta elección? Esta acción no se puede deshacer.')) return;
    try {
      await requestJson(`/api/elecciones/${idEleccion}/iniciar`, { method: 'POST' });
      showToast('Elección iniciada correctamente', 'success');
      await Promise.all([loadElecciones(), loadStats()]);
    } catch (error) {
      showToast('Error al iniciar la elección', 'error');
    }
  };

  window.cerrarEleccion = async function cerrarEleccion(idEleccion) {
    if (!confirm('¿Está seguro de cerrar esta elección?')) return;
    try {
      await requestJson(`/api/elecciones/${idEleccion}/cerrar`, { method: 'POST' });
      showToast('Elección cerrada correctamente', 'success');
      await Promise.all([loadElecciones(), loadStats()]);
    } catch (error) {
      showToast('Error al cerrar la elección', 'error');
    }
  };

  window.eliminarEleccion = async function eliminarEleccion(idEleccion) {
    if (!confirm('¿Eliminar esta elección?')) return;
    try {
      await requestJson(`/api/elecciones/${idEleccion}`, { method: 'DELETE' });
      showToast('Elección eliminada', 'success');
      await Promise.all([loadElecciones(), loadStats()]);
    } catch (error) {
      showToast('No se pudo eliminar la elección', 'error');
    }
  };

  window.verCandidatos = function verCandidatos() {
    showToast('Gestión de candidatos disponible desde el módulo de elección.', 'warning');
  };

  window.verResumen = function verResumen() {
    showToast('Resumen electoral en preparación.', 'warning');
  };

  window.abrirDetalleEleccion = function abrirDetalleEleccion() {
    showToast('Detalle de elección en preparación.', 'warning');
  };

  window.toggleMenuEleccion = function toggleMenuEleccion(event, id) {
    event.stopPropagation();
    const election = state.elecciones.find((e) => Number(e.id) === Number(id));
    const estado = normalizarEstado(election?.estado);
    const menu = document.getElementById('dropdown-elecciones');
    const rect = event.currentTarget.getBoundingClientRect();
    menu.classList.remove('hidden');
    menu.style.left = `${Math.max(16, rect.right - 180)}px`;
    menu.style.top = `${rect.bottom + 8}px`;
    menu.innerHTML = menuItems(id, estado);
  };

  function menuItems(id, estado) {
    if (estado === 'PROGRAMADA') {
      return `
        <button type="button" onclick="editarEleccion(${id})">Editar</button>
        <button type="button" onclick="verCandidatos(${id})">Agregar candidatos</button>
        <button type="button" class="danger" onclick="eliminarEleccion(${id})">Eliminar</button>
      `;
    }
    if (estado === 'EN_CURSO') {
      return `
        <button type="button" onclick="verResumen(${id})">Ver en tiempo real</button>
        <button type="button" class="danger" onclick="cerrarEleccion(${id})">Cerrar elección</button>
      `;
    }
    if (estado === 'FINALIZADA') {
      return `
        <button type="button" onclick="verResumen(${id})">Descargar reporte</button>
        <button type="button" onclick="verResumen(${id})">Ver certificados</button>
      `;
    }
    return `
      <button type="button" onclick="abrirDetalleEleccion(${id})">Ver detalle</button>
      <button type="button" class="danger" onclick="eliminarEleccion(${id})">Eliminar</button>
    `;
  }

  function mostrarErrorModal(message) {
    const error = document.getElementById('modal-eleccion-error');
    error.textContent = message;
    error.classList.add('visible');
  }

  function ocultarErrorModal() {
    const error = document.getElementById('modal-eleccion-error');
    error.textContent = '';
    error.classList.remove('visible');
  }

  function showToast(message, type = 'success') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  document.addEventListener('click', (event) => {
    const menu = document.getElementById('dropdown-elecciones');
    if (menu && !menu.contains(event.target)) {
      menu.classList.add('hidden');
    }
  });

  document.getElementById('buscar-eleccion')?.addEventListener('input', (event) => {
    const term = event.target.value.toLowerCase();
    state.filtradas = state.elecciones.filter((e) => String(e.nombre || '').toLowerCase().includes(term));
    state.pagina = 1;
    renderEleccionesTable();
  });

  window.loadElecciones = loadElecciones;
  window.renderEleccionesTable = renderEleccionesTable;
  Promise.all([loadElecciones(), loadStats()]);
})();
