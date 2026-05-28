(function initCandidatosPage() {
  const CARGOS_BASE = ['Rector', 'Personero', 'Contralor', 'Representante Estudiantil'];

  const state = {
    elecciones: [],
    eleccionId: '',
    eleccionActual: null,
    candidatos: [],
    filtrados: [],
    gruposColapsados: new Set(),
    editandoId: null,
    auditoria: []
  };

  const dom = {
    selectEleccion: document.getElementById('selectEleccion'),
    statusBadge: document.getElementById('eleccionStatusBadge'),
    stats: document.getElementById('candidatosStats'),
    content: document.getElementById('candidatosContent'),
    error: document.getElementById('candidatosError'),
    tarjeton: document.getElementById('tarjetonPreview'),
    auditoria: document.getElementById('auditoriaList'),
    filtroBusqueda: document.getElementById('filtroBusquedaCandidato'),
    filtroCargo: document.getElementById('filtroCargoCandidato'),
    filtroEstado: document.getElementById('filtroEstadoCandidato'),
    filtroOrden: document.getElementById('filtroOrdenCandidato'),
    modal: document.getElementById('modalCandidato'),
    modalTitle: document.getElementById('modalCandidatoTitulo'),
    form: document.getElementById('formCandidato'),
    modalError: document.getElementById('modalCandidatoError'),
    modalDelete: document.getElementById('modalEliminarCandidato'),
    modalDeleteContent: document.getElementById('modalEliminarContenido'),
    cargoForm: document.getElementById('cargoCandidato'),
    photoInput: document.getElementById('fotoCandidato'),
    photoPreview: document.getElementById('fotoPreview')
  };

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (char) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }[char]));
  }

  function normalizeText(value) {
    return String(value || '').trim();
  }

  function normalizeUpper(value) {
    return normalizeText(value).toUpperCase();
  }

  function number(value) {
    return new Intl.NumberFormat('es-CO').format(Number(value) || 0);
  }

  function candidateId(candidato) {
    return candidato.idCandidato ?? candidato.id_candidato ?? candidato.id ?? null;
  }

  function electionId(eleccion) {
    return eleccion.id ?? eleccion.idEleccion ?? eleccion.id_eleccion ?? null;
  }

  function numeroCampania(candidato) {
    return Number(candidato.numeroCampania ?? candidato.numero_campania ?? candidato.numero ?? 0);
  }

  function votosCandidato(candidato) {
    return Number(candidato.votos ?? candidato.totalVotos ?? candidato.total_votos ?? 0);
  }

  function estadoCandidato(candidato) {
    return normalizeUpper(candidato.estado ?? candidato.estadoCandidato ?? 'ACTIVO') || 'ACTIVO';
  }

  function nombreCompleto(candidato) {
    return [
      candidato.primerNombre ?? candidato.primer_nombre,
      candidato.segundoNombre ?? candidato.segundo_nombre,
      candidato.primerApellido ?? candidato.primer_apellido,
      candidato.segundoApellido ?? candidato.segundo_apellido
    ].filter(Boolean).join(' ').replace(/\s+/g, ' ').trim() || 'Sin nombre registrado';
  }

  function iniciales(candidato) {
    return nombreCompleto(candidato)
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('') || 'CD';
  }

  function icon(name) {
    const icons = {
      users: '<svg viewBox="0 0 24 24"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
      vote: '<svg viewBox="0 0 24 24"><path d="m9 12 2 2 4-4"/><path d="M5 4h14v6H5z"/><path d="M3 14h18v6H3z"/></svg>',
      briefcase: '<svg viewBox="0 0 24 24"><path d="M10 6V5a2 2 0 0 1 2-2h0a2 2 0 0 1 2 2v1"/><path d="M3 7h18v12H3z"/><path d="M3 12h18"/></svg>',
      shield: '<svg viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/></svg>',
      edit: '<svg viewBox="0 0 24 24"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>',
      trash: '<svg viewBox="0 0 24 24"><path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v5"/><path d="M14 11v5"/></svg>',
      plus: '<svg viewBox="0 0 24 24"><path d="M12 5v14"/><path d="M5 12h14"/></svg>',
      move: '<svg viewBox="0 0 24 24"><path d="M7 7h10M7 17h10"/><path d="m14 4 3 3-3 3"/><path d="m10 14-3 3 3 3"/></svg>',
      check: '<svg viewBox="0 0 24 24"><path d="m20 6-11 11-5-5"/></svg>',
      blocked: '<svg viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/><path d="M9 12h6"/></svg>'
    };
    return icons[name] || icons.users;
  }

  function showError(message) {
    if (window.showToast) window.showToast(message || 'Ocurrio un error cargando candidatos.', 'error');
  }

  function clearError() {}

  function unwrap(response) {
    return response?.data ?? response ?? [];
  }

  async function cargarElecciones() {
    clearError();
    try {
      const payload = await ApiElecciones.listar();
      state.elecciones = unwrap(payload);
      renderizarSelectorElecciones();
      const preferida = localStorage.getItem('abis_eleccion_candidatos');
      const seleccionada = preferida
        ? state.elecciones.find((item) => String(electionId(item)) === String(preferida))
        : null;
      const activa = seleccionada || state.elecciones.find((item) => normalizeUpper(item.estado) === 'EN_CURSO') || state.elecciones[0];
      localStorage.removeItem('abis_eleccion_candidatos');
      if (activa) {
        state.eleccionId = String(electionId(activa));
        state.eleccionActual = activa;
        dom.selectEleccion.value = state.eleccionId;
        actualizarEstadoEleccion();
        await cargarCandidatosPorEleccion(state.eleccionId);
      } else {
        dom.selectEleccion.innerHTML = '<option value="">No hay elecciones registradas</option>';
        renderizarTodo();
      }
    } catch (error) {
      dom.selectEleccion.innerHTML = '<option value="">No fue posible cargar elecciones</option>';
      showError(error.message);
      renderizarTodo();
    }
  }

  function renderizarSelectorElecciones() {
    dom.selectEleccion.innerHTML = '<option value="">Seleccione una elección...</option>' + state.elecciones.map((eleccion) => {
      const id = electionId(eleccion);
      return `<option value="${escapeHtml(id)}">${escapeHtml(eleccion.nombre || 'Elección sin nombre')}</option>`;
    }).join('');
  }

  function actualizarEstadoEleccion() {
    state.eleccionActual = state.elecciones.find((item) => String(electionId(item)) === String(state.eleccionId)) || null;
    const estado = normalizeUpper(state.eleccionActual?.estado || 'SIN ESTADO');
    dom.statusBadge.textContent = estado.replace('_', ' ');
    dom.statusBadge.className = 'eleccion-status-badge';
    if (estado === 'PROGRAMADA') dom.statusBadge.classList.add('programada');
    if (estado === 'CERRADA' || estado === 'FINALIZADA') dom.statusBadge.classList.add('finalizada');
  }

  async function cargarCandidatosPorEleccion(eleccionIdValue) {
    clearError();
    state.eleccionId = String(eleccionIdValue || '');
    actualizarEstadoEleccion();
    dom.content.classList.add('is-loading');
    if (!state.eleccionId) {
      state.candidatos = [];
      state.filtrados = [];
      renderizarTodo();
      dom.content.classList.remove('is-loading');
      return;
    }

    try {
      const payload = await ApiElecciones.candidatos(state.eleccionId);
      state.candidatos = unwrap(payload).map(normalizarCandidato);
      state.filtrados = [...state.candidatos];
      poblarFiltrosCargo();
      aplicarFiltros();
      cargarAuditoria(state.eleccionId);
    } catch (error) {
      state.candidatos = [];
      state.filtrados = [];
      showError(error.message);
      renderizarTodo();
    } finally {
      dom.content.classList.remove('is-loading');
    }
  }

  async function refrescarCandidatosEnVivo() {
    if (!state.eleccionId || document.hidden) return;
    if (!document.body.contains(dom.content)) {
      clearInterval(window.__candidatosLiveRefresh);
      window.__candidatosLiveRefresh = null;
      return;
    }
    if (dom.modal.classList.contains('open') || dom.modalDelete.classList.contains('open')) return;

    try {
      const payload = await ApiElecciones.candidatos(state.eleccionId);
      state.candidatos = unwrap(payload).map(normalizarCandidato);
      state.filtrados = [...state.candidatos];
      poblarFiltrosCargo();
      aplicarFiltros();
    } catch (error) {
      console.warn('No fue posible refrescar candidatos en vivo:', error);
    }
  }

  function iniciarActualizacionEnVivo() {
    if (window.__candidatosLiveRefresh) {
      clearInterval(window.__candidatosLiveRefresh);
    }
    window.__candidatosLiveRefresh = setInterval(refrescarCandidatosEnVivo, 10000);
  }

  function normalizarCandidato(candidato) {
    return {
      ...candidato,
      idCandidato: candidateId(candidato),
      idEleccion: candidato.idEleccion ?? candidato.id_eleccion ?? state.eleccionId,
      numeroCampania: numeroCampania(candidato),
      cargo: normalizeText(candidato.cargo || 'SIN CARGO'),
      votos: votosCandidato(candidato),
      estado: estadoCandidato(candidato),
      nombreCompleto: nombreCompleto(candidato)
    };
  }

  function poblarFiltrosCargo() {
    const cargos = [...new Set([...CARGOS_BASE, ...state.candidatos.map((item) => item.cargo).filter(Boolean)])].sort();
    dom.filtroCargo.innerHTML = '<option value="">Todos</option>' + cargos.map((cargo) =>
      `<option value="${escapeHtml(cargo)}">${escapeHtml(cargo)}</option>`
    ).join('');
    dom.cargoForm.innerHTML = cargos.map((cargo) =>
      `<option value="${escapeHtml(cargo)}">${escapeHtml(cargo)}</option>`
    ).join('');
  }

  function agruparPorCargo(candidatos) {
    return candidatos.reduce((grupos, candidato) => {
      const cargo = candidato.cargo || 'SIN CARGO';
      if (!grupos[cargo]) grupos[cargo] = [];
      grupos[cargo].push(candidato);
      return grupos;
    }, {});
  }

  function ordenarCandidatos(items) {
    const orden = dom.filtroOrden.value;
    return [...items].sort((a, b) => {
      if (orden === 'nombre') return a.nombreCompleto.localeCompare(b.nombreCompleto, 'es');
      if (orden === 'cargo') return a.cargo.localeCompare(b.cargo, 'es') || a.numeroCampania - b.numeroCampania;
      if (orden === 'votos') return b.votos - a.votos || a.numeroCampania - b.numeroCampania;
      return a.numeroCampania - b.numeroCampania;
    });
  }

  function aplicarFiltros() {
    const busqueda = normalizeText(dom.filtroBusqueda.value).toLowerCase();
    const cargo = dom.filtroCargo.value;
    const estado = dom.filtroEstado.value;

    state.filtrados = ordenarCandidatos(state.candidatos.filter((candidato) => {
      const texto = [
        candidato.idCandidato,
        candidato.nombreCompleto,
        candidato.cargo,
        candidato.numeroCampania
      ].join(' ').toLowerCase();

      if (busqueda && !texto.includes(busqueda)) return false;
      if (cargo && candidato.cargo !== cargo) return false;
      if (estado === 'con_votos' && candidato.votos <= 0) return false;
      if (estado === 'sin_votos' && candidato.votos > 0) return false;
      if (estado === 'activo' && candidato.estado !== 'ACTIVO') return false;
      if (estado === 'inactivo' && candidato.estado !== 'INACTIVO') return false;
      return true;
    }));

    renderizarTodo();
  }

  function renderizarTodo() {
    const grupos = agruparPorCargo(state.filtrados);
    renderizarStats();
    renderizarGruposCargo(grupos);
    renderizarTarjetonPreview(agruparPorCargo(state.candidatos));
  }

  function renderizarStats() {
    const total = state.candidatos.length;
    const cargos = new Set(state.candidatos.map((item) => item.cargo)).size;
    const conVotos = state.candidatos.filter((item) => item.votos > 0).length;
    const porcentaje = total ? Math.round((conVotos / total) * 100) : 0;
    const estado = normalizeUpper(state.eleccionActual?.estado || 'SIN ESTADO').replace('_', ' ');
    const tarjeton = total > 0 && cargos > 0 ? 'VALIDADO' : 'PENDIENTE';

    dom.stats.innerHTML = [
      ['users', 'TOTAL CANDIDATOS', number(total), 'Del total registrado'],
      ['vote', 'ELECCIÓN ACTIVA', estado, state.eleccionActual?.nombre || 'Sin elección seleccionada'],
      ['briefcase', 'CARGOS CONFIGURADOS', number(cargos), 'Cargos distintos en tarjetón'],
      ['check', 'CON VOTOS', number(conVotos), `${porcentaje}% del total`],
      ['shield', 'TARJETÓN', tarjeton, total ? 'Listo para vista previa' : 'Pendiente de candidatos']
    ].map(([iconName, label, value, note]) => {
      const isTextStatus = String(value).length > 4 && !/^\d/.test(String(value));
      const displayValue = isTextStatus
        ? `<span class="stat-value-badge stat-value-badge-${String(value).toLowerCase().replace('_', '-')}">${escapeHtml(value)}</span>`
        : `<span class="candidate-stat-value-num">${escapeHtml(value)}</span>`;
      return `
      <article class="candidate-stat-card">
        <span class="candidate-stat-icon">${icon(iconName)}</span>
        <div>
          <p class="candidate-stat-label">${label}</p>
          ${displayValue}
          <p class="candidate-stat-note">${escapeHtml(note)}</p>
        </div>
      </article>
    `;}).join('');
  }

  function renderizarGruposCargo(candidatosAgrupados) {
    const entries = Object.entries(candidatosAgrupados);
    if (!state.eleccionId) {
      dom.content.innerHTML = '<div class="candidatos-empty">Seleccione una elección para administrar sus candidatos.</div>';
      return;
    }

    if (!entries.length) {
      dom.content.innerHTML = '<div class="candidatos-empty">No hay candidatos que coincidan con los filtros aplicados.</div>';
      return;
    }

    dom.content.innerHTML = entries.map(([cargo, items]) => {
      const totalVotos = items.reduce((sum, item) => sum + item.votos, 0);
      const collapsed = state.gruposColapsados.has(cargo);
      return `
        <section class="cargo-section ${collapsed ? 'collapsed' : ''}" data-cargo="${escapeHtml(cargo)}">
          <div class="cargo-section-header" onclick="toggleCargo('${escapeAttribute(cargo)}')">
            <div class="cargo-info">
              <span class="cargo-icon">${icon('briefcase')}</span>
              <span class="cargo-nombre">${escapeHtml(cargo)}</span>
              <span class="cargo-count-badge">${pluralizarCandidatos(items.length)}</span>
            </div>
            <div class="cargo-meta">
              <span class="cargo-total-votos">Total votos: ${number(totalVotos)}</span>
              <span class="cargo-chevron">▲</span>
            </div>
          </div>
          <div class="cargo-candidates-grid">
            ${items.map(renderizarCardCandidato).join('')}
            <div class="candidate-card candidate-card-add" onclick="abrirModalNuevo('${escapeAttribute(cargo)}')">
              <div class="add-icon">+</div>
              <span>Agregar candidato al cargo ${escapeHtml(cargo)}</span>
            </div>
          </div>
        </section>
      `;
    }).join('');
  }

  function pluralizarCandidatos(count) {
    return count === 1 ? '1 candidato' : `${number(count)} candidatos`;
  }

  function escapeAttribute(value) {
    return String(value || '').replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, ' ');
  }

  function renderizarCardCandidato(candidato) {
    const id = candidateId(candidato);
    const numero = candidato.numeroCampania ? String(candidato.numeroCampania).padStart(2, '0') : '--';
    const tieneVotos = candidato.votos > 0;
    const activo = candidato.estado !== 'INACTIVO';
    const foto = candidato.fotoUrl || candidato.foto_url;
    const avatar = foto
      ? `<img src="${escapeHtml(foto)}" alt="Foto de ${escapeHtml(candidato.nombreCompleto)}" class="candidate-photo">`
      : `<div class="candidate-avatar-initials">${escapeHtml(iniciales(candidato))}</div>`;

    return `
      <div class="candidate-card">
        <div class="candidate-number">${escapeHtml(numero)}</div>
        ${avatar}
        <div class="candidate-info">
          <span class="candidate-name" title="${escapeHtml(candidato.nombreCompleto)}">${escapeHtml(candidato.nombreCompleto)}</span>
          <span class="candidate-faculty">Postulación a ${escapeHtml(candidato.cargo)}</span>
        </div>
        <div class="candidate-actions">
          <button class="btn-icon" onclick="editarCandidato(${Number(id)})" title="Editar" type="button">${icon('edit')}</button>
          <button class="btn-icon btn-icon-danger" onclick="eliminarCandidato(${Number(id)})" title="Eliminar" type="button">${icon('trash')}</button>
        </div>
        <div class="candidate-badges">
          <span class="badge-votos ${tieneVotos ? 'con-votos' : 'sin-votos'}">${tieneVotos ? icon('check') + 'CON VOTOS' : 'SIN VOTOS'}</span>
          <span class="badge-estado ${activo ? 'activo' : 'inactivo'}">${activo ? 'ACTIVO' : 'INACTIVO'}</span>
        </div>
      </div>
    `;
  }

  function toggleCargo(cargoNombre) {
    if (state.gruposColapsados.has(cargoNombre)) {
      state.gruposColapsados.delete(cargoNombre);
    } else {
      state.gruposColapsados.add(cargoNombre);
    }
    renderizarGruposCargo(agruparPorCargo(state.filtrados));
  }

  function abrirModalNuevo(cargoPreseleccionado = null) {
    state.editandoId = null;
    dom.modalTitle.textContent = 'Nuevo candidato';
    dom.form.reset();
    document.getElementById('candidateId').value = '';
    if (cargoPreseleccionado) dom.cargoForm.value = cargoPreseleccionado;
    dom.photoPreview.innerHTML = 'Sin foto';
    limpiarErroresFormulario();
    abrirModal(dom.modal);
  }

  function editarCandidato(candidatoIdValue) {
    const candidato = state.candidatos.find((item) => Number(candidateId(item)) === Number(candidatoIdValue));
    if (!candidato) return;
    state.editandoId = candidateId(candidato);
    dom.modalTitle.textContent = 'Editar candidato';
    document.getElementById('candidateId').value = state.editandoId;
    document.getElementById('primerNombre').value = candidato.primerNombre ?? candidato.primer_nombre ?? '';
    document.getElementById('segundoNombre').value = candidato.segundoNombre ?? candidato.segundo_nombre ?? '';
    document.getElementById('primerApellido').value = candidato.primerApellido ?? candidato.primer_apellido ?? '';
    document.getElementById('segundoApellido').value = candidato.segundoApellido ?? candidato.segundo_apellido ?? '';
    document.getElementById('numeroCampania').value = candidato.numeroCampania || '';
    dom.cargoForm.value = candidato.cargo;
    dom.photoPreview.innerHTML = candidato.fotoUrl || candidato.foto_url
      ? `<img src="${escapeHtml(candidato.fotoUrl || candidato.foto_url)}" alt="Foto de ${escapeHtml(candidato.nombreCompleto)}">`
      : 'Sin foto';
    limpiarErroresFormulario();
    abrirModal(dom.modal);
  }

  function validarNumeroCampana(numero, eleccionIdValue, excludeId = null) {
    const normalized = Number(numero);
    if (!normalized) return null;
    return state.candidatos.find((candidato) =>
      Number(candidato.numeroCampania) === normalized &&
      String(candidato.idEleccion ?? state.eleccionId) === String(eleccionIdValue) &&
      Number(candidateId(candidato)) !== Number(excludeId)
    ) || null;
  }

  function obtenerPayloadFormulario() {
    return {
      primerNombre: normalizeText(document.getElementById('primerNombre').value),
      segundoNombre: normalizeText(document.getElementById('segundoNombre').value),
      primerApellido: normalizeText(document.getElementById('primerApellido').value),
      segundoApellido: normalizeText(document.getElementById('segundoApellido').value),
      email: normalizeText(document.getElementById('email').value),
      numeroCampania: normalizeText(document.getElementById('numeroCampania').value),
      cargo: normalizeText(dom.cargoForm.value)
    };
  }

  function construirPayloadCandidato(payload) {
    const foto = dom.photoInput.files?.[0] || null;
    if (!foto) return payload;

    const formData = new FormData();
    Object.entries(payload).forEach(([key, value]) => {
      formData.append(key, value ?? '');
    });
    formData.append('foto', foto);
    return formData;
  }

  function limpiarErroresFormulario() {
    dom.modalError.textContent = '';
    dom.modalError.classList.remove('visible');
    // modal error handled by showToast in catch block
    dom.form.querySelectorAll('.invalid').forEach((field) => field.classList.remove('invalid'));
    dom.form.querySelectorAll('.field-error').forEach((field) => { field.textContent = ''; });
  }

  function setFieldError(id, message) {
    const field = document.getElementById(id);
    const error = field?.parentElement?.querySelector('.field-error');
    if (field) field.classList.add('invalid');
    if (error) error.textContent = message;
  }

  function validarFormulario(payload) {
    limpiarErroresFormulario();
    let valid = true;
    if (!payload.primerNombre) {
      setFieldError('primerNombre', 'Primer nombre requerido');
      valid = false;
    }
    if (!payload.primerApellido) {
      setFieldError('primerApellido', 'Primer apellido requerido');
      valid = false;
    }
    if (!payload.numeroCampania || Number(payload.numeroCampania) <= 0 || Number(payload.numeroCampania) > 9999) {
      setFieldError('numeroCampania', 'Numero de campana requerido (1-9999)');
      valid = false;
    }
    if (payload.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(payload.email)) {
      setFieldError('email', 'Formato de correo invalido');
      valid = false;
    }
    if (!payload.cargo) {
      setFieldError('cargoCandidato', 'Cargo requerido');
      valid = false;
    }

    const duplicado = validarNumeroCampana(payload.numeroCampania, state.eleccionId, state.editandoId);
    if (duplicado) {
      setFieldError('numeroCampania', `El número ${payload.numeroCampania} ya está asignado a ${nombreCompleto(duplicado)} en esta elección`);
      valid = false;
    }
    return valid;
  }

  async function guardarCandidato(formData) {
    const requestBody = construirPayloadCandidato(formData);
    if (!state.eleccionId) throw new Error('Seleccione una elección antes de guardar.');
    if (state.editandoId) {
      await ApiCandidatos.editar(state.eleccionId, state.editandoId, requestBody);
      registrarAuditoria('edit', 'Candidato actualizado', `${formData.primerNombre} ${formData.primerApellido}`, `Cargo: ${formData.cargo}`);
    } else {
      await ApiCandidatos.agregar(state.eleccionId, requestBody);
      registrarAuditoria('create', 'Candidato creado', `${formData.primerNombre} ${formData.primerApellido}`, `Número ${formData.numeroCampania}`);
    }
    cerrarModal(dom.modal);
    await cargarCandidatosPorEleccion(state.eleccionId);
  }

  async function eliminarCandidato(candidatoIdValue) {
    const candidato = state.candidatos.find((item) => Number(candidateId(item)) === Number(candidatoIdValue));
    if (!candidato) return;
    if (candidato.votos > 0) {
      mostrarEliminacionBloqueada(candidato, 'No es posible eliminar este candidato porque tiene votos asociados.');
      return;
    }
    mostrarConfirmacionEliminar(candidato);
  }

  function mostrarConfirmacionEliminar(candidato) {
    dom.modalDeleteContent.innerHTML = `
      <h2 id="modalEliminarTitulo" class="confirm-title">Eliminar candidato</h2>
      <p class="confirm-message">¿Confirmas la eliminación de ${escapeHtml(candidato.nombreCompleto)}? Esta acción no se puede deshacer.</p>
      <div class="confirm-actions">
        <button class="btn-outline" type="button" onclick="cerrarModalEliminarCandidato()">Cancelar</button>
        <button class="btn-primary btn-danger" type="button" onclick="confirmarEliminarCandidato(${Number(candidateId(candidato))})">Eliminar</button>
      </div>
    `;
    abrirModal(dom.modalDelete);
  }

  async function confirmarEliminarCandidato(candidatoIdValue) {
    const candidato = state.candidatos.find((item) => Number(candidateId(item)) === Number(candidatoIdValue));
    if (!candidato) return;
    try {
      const response = await fetch(`/api/elecciones/${encodeURIComponent(state.eleccionId)}/candidatos/${encodeURIComponent(candidatoIdValue)}`, {
        method: 'DELETE'
      });
      const body = await response.json().catch(() => ({}));
      if (response.status === 409) {
        mostrarEliminacionBloqueada(candidato, body.error || body.message || 'No se puede eliminar: tiene votos asociados');
        registrarAuditoria('blocked', 'Eliminación bloqueada', candidato.nombreCompleto, 'El backend detectó votos asociados');
        return;
      }
      if (!response.ok) {
        throw new Error(body.error || body.message || `HTTP ${response.status}`);
      }
      registrarAuditoria('delete', 'Candidato eliminado', candidato.nombreCompleto, `Número ${candidato.numeroCampania}`);
      cerrarModal(dom.modalDelete);
      await cargarCandidatosPorEleccion(state.eleccionId);
    } catch (error) {
      mostrarEliminacionBloqueada(candidato, error.message);
    }
  }

  function mostrarEliminacionBloqueada(candidato, detalle) {
    const votosTexto = candidato.votos > 0
      ? `tiene ${number(candidato.votos)} votos asociados`
      : 'tiene votos asociados';
    dom.modalDeleteContent.innerHTML = `
      <div class="confirm-icon">${icon('blocked')}</div>
      <h2 id="modalEliminarTitulo" class="confirm-title">Eliminación bloqueada</h2>
      <p class="confirm-message">No es posible eliminar a ${escapeHtml(candidato.nombreCompleto)} porque ${votosTexto} en la elección ${escapeHtml(state.eleccionActual?.nombre || 'seleccionada')}. Para retirarlo, cámbialo a estado Inactivo.</p>
      <p class="audit-detail">${escapeHtml(detalle || '')}</p>
      <div class="confirm-actions">
        <button class="btn-primary" type="button" onclick="cerrarModalEliminarCandidato()">Entendido</button>
      </div>
    `;
    abrirModal(dom.modalDelete);
  }

  function renderizarTarjetonPreview(candidatosAgrupados) {
    const entries = Object.entries(candidatosAgrupados);
    if (!entries.length) {
      dom.tarjeton.innerHTML = '<div class="candidatos-empty">Sin candidatos para previsualizar.</div>';
      return;
    }
    dom.tarjeton.innerHTML = entries.map(([cargo, items]) => `
      <section class="tarjeton-cargo">
        <h3 class="tarjeton-cargo-title">${escapeHtml(cargo)}</h3>
        <div class="tarjeton-mini-grid">
          ${opcionesPreviewTarjeton(items).map((candidato) => `
            <div class="tarjeton-mini${candidato.esVotoBlanco ? ' blank' : ''}">
              <span class="tarjeton-mini-num">${candidato.esVotoBlanco ? 'VB' : String(candidato.numeroCampania || '--').padStart(2, '0')}</span>
              <span class="tarjeton-mini-name">${escapeHtml(candidato.nombreCompleto || candidato.nombre)}</span>
              <span class="tarjeton-mini-party">${escapeHtml(candidato.movimiento || candidato.partido || (candidato.esVotoBlanco ? 'Opcion oficial' : 'Universitario'))}</span>
            </div>
          `).join('')}
        </div>
      </section>
    `).join('') + '<div class="tarjeton-legend">Números de campaña en formato de tarjetón electoral.</div>';
  }

  function opcionesPreviewTarjeton(items) {
    const ordenados = ordenarCandidatos(items).map((candidato) => ({
      ...candidato,
      esVotoBlanco: candidateId(candidato) == null ||
        /voto\s+en\s+blanco/i.test(String(candidato.nombreCompleto || candidato.nombre || ''))
    }));
    const tieneVotoBlanco = ordenados.some((candidato) =>
      candidateId(candidato) == null ||
      /voto\s+en\s+blanco/i.test(String(candidato.nombreCompleto || candidato.nombre || ''))
    );
    if (tieneVotoBlanco) return ordenados;
    return [
      ...ordenados,
      {
        idCandidato: null,
        numeroCampania: '',
        nombreCompleto: 'Voto en blanco',
        movimiento: 'Opcion oficial',
        esVotoBlanco: true
      }
    ];
  }

  function cargarAuditoria() {
    if (!state.auditoria.length) {
      state.auditoria = [
        { type: 'create', title: 'Creación disponible', detail: 'Alta de candidatos conectada al backend Java', user: 'Sistema ABIS', date: 'Ahora' },
        { type: 'edit', title: 'Edición operativa', detail: 'El backend actualiza número de campaña y cargo', user: 'Sistema ABIS', date: 'Ahora' },
        { type: 'blocked', title: 'Eliminación protegida', detail: 'Si existen votos, el backend responde 409 Conflict', user: 'Sistema ABIS', date: 'Ahora' }
      ];
    }
    renderizarAuditoria();
  }

  function registrarAuditoria(type, title, detail, user) {
    state.auditoria.unshift({
      type,
      title,
      detail,
      user: user || 'Administrador',
      date: new Date().toLocaleString('es-CO')
    });
    state.auditoria = state.auditoria.slice(0, 8);
    renderizarAuditoria();
  }

  function renderizarAuditoria() {
    const iconByType = {
      create: 'plus',
      edit: 'edit',
      delete: 'trash',
      blocked: 'shield',
      move: 'move'
    };
    dom.auditoria.innerHTML = state.auditoria.slice(0, 6).map((item) => `
      <article class="audit-row">
        <span class="audit-icon ${escapeHtml(item.type)}">${icon(iconByType[item.type] || 'shield')}</span>
        <div>
          <p class="audit-title">${escapeHtml(item.title)}</p>
          <p class="audit-detail">${escapeHtml(item.detail)}</p>
          <p class="audit-meta">${escapeHtml(item.user)} · ${escapeHtml(item.date)}</p>
        </div>
      </article>
    `).join('');
  }

  function abrirModal(modal) {
    modal.removeAttribute('inert');
    modal.classList.add('open');
    modal.setAttribute('aria-hidden', 'false');
  }

  function cerrarModal(modal) {
    if (modal.contains(document.activeElement)) {
      document.activeElement.blur();
    }
    modal.classList.remove('open');
    modal.setAttribute('aria-hidden', 'true');
    modal.setAttribute('inert', '');
  }

  function exportarCsv() {
    const rows = state.filtrados.map((candidato) => [
      candidato.idCandidato,
      candidato.numeroCampania,
      candidato.nombreCompleto,
      candidato.cargo,
      candidato.estado,
      candidato.votos
    ]);
    const csv = [
      ['id_candidato', 'numero_campania', 'nombre', 'cargo', 'estado', 'votos'],
      ...rows
    ].map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `candidatos-eleccion-${state.eleccionId || 'sin-eleccion'}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  function abrirTarjetonCompleto() {
    const printWindow = window.open('', '_blank');
    if (!printWindow) return;
    printWindow.document.write(`
      <!doctype html>
      <html lang="es">
      <head>
        <meta charset="UTF-8">
        <title>Tarjetón ABIS-UPC</title>
        <style>
          body { font-family: Arial, sans-serif; color: #1f2d26; padding: 24px; }
          h1 { color: #1a6b3c; }
          section { border-top: 1px solid #e2e8e4; padding: 14px 0; }
          .num { color: #1a6b3c; font-weight: 800; margin-right: 8px; }
        </style>
      </head>
      <body>
        <h1>Tarjetón ABIS-UPC</h1>
        ${Object.entries(agruparPorCargo(state.candidatos)).map(([cargo, items]) => `
          <section>
            <h2>${escapeHtml(cargo)}</h2>
            ${ordenarCandidatos(items).map((c) => `<p><span class="num">${String(c.numeroCampania).padStart(2, '0')}</span>${escapeHtml(c.nombreCompleto)}</p>`).join('')}
          </section>
        `).join('')}
      </body>
      </html>
    `);
    printWindow.document.close();
  }

  dom.selectEleccion.addEventListener('change', async () => {
    state.eleccionId = dom.selectEleccion.value;
    await cargarCandidatosPorEleccion(state.eleccionId);
  });

  dom.filtroBusqueda.addEventListener('input', () => {
    clearTimeout(window.__candidatosSearchTimer);
    window.__candidatosSearchTimer = setTimeout(aplicarFiltros, 180);
  });
  dom.filtroCargo.addEventListener('change', aplicarFiltros);
  dom.filtroEstado.addEventListener('change', aplicarFiltros);
  dom.filtroOrden.addEventListener('change', aplicarFiltros);
  document.getElementById('btnAplicarFiltrosCandidatos').addEventListener('click', aplicarFiltros);
  document.getElementById('btnLimpiarFiltrosCandidatos').addEventListener('click', () => {
    dom.filtroBusqueda.value = '';
    dom.filtroCargo.value = '';
    dom.filtroEstado.value = '';
    dom.filtroOrden.value = 'numero';
    aplicarFiltros();
  });
  document.getElementById('btnNuevoCandidato').addEventListener('click', () => abrirModalNuevo());
  document.getElementById('btnExportar').addEventListener('click', exportarCsv);
  document.getElementById('btnAuditoria').addEventListener('click', () => document.querySelector('.candidatos-sidebar')?.scrollIntoView({ behavior: 'smooth' }));
  document.getElementById('btnVerAuditoriaCompleta').addEventListener('click', (event) => {
    event.preventDefault();
    document.querySelector('.candidatos-sidebar')?.scrollIntoView({ behavior: 'smooth' });
  });
  function abrirTarjetonCompletoVisual() {
    const printWindow = window.open('', '_blank');
    if (!printWindow) return;
    printWindow.document.write(`
      <!doctype html>
      <html lang="es">
      <head>
        <meta charset="UTF-8">
        <title>Tarjeton ABIS-UPC</title>
        <style>
          * { box-sizing: border-box; }
          body { margin: 0; background: #f4f7f5; color: #1f2925; font-family: "DM Sans", Arial, sans-serif; padding: 28px; }
          .sheet { background: #fff; border: 2px solid #1f2925; border-radius: 8px; margin: 0 auto 24px; max-width: 980px; overflow: hidden; }
          .head { align-items: start; background: #fbfcfb; display: grid; gap: 20px; grid-template-columns: 1fr 2fr 1fr; padding: 24px 30px 16px; }
          .brand { color: #075521; display: grid; font-weight: 900; gap: 7px; text-transform: uppercase; }
          .flag { display: grid; grid-template-columns: 2fr 1fr 1fr; height: 12px; width: 126px; }
          .flag span:nth-child(1) { background: #fcd116; }
          .flag span:nth-child(2) { background: #003893; }
          .flag span:nth-child(3) { background: #ce1126; }
          .year { font-size: 24px; }
          .title { text-align: center; }
          .title small { color: #5f6f67; font-size: 12px; font-weight: 900; letter-spacing: .12em; text-transform: uppercase; }
          .title h1 { border-bottom: 1px solid #1f2925; color: #1f2925; font-size: 25px; line-height: 1.12; margin: 4px 0 6px; padding-bottom: 8px; text-transform: uppercase; }
          .code { justify-self: end; text-align: right; }
          .bars { background: repeating-linear-gradient(90deg, #1f2925 0 2px, transparent 2px 5px, #1f2925 5px 6px, transparent 6px 9px); display: block; height: 36px; width: 132px; }
          .code strong { display: block; font-family: monospace; font-size: 11px; letter-spacing: .18em; margin-top: 6px; }
          .instruction { border-bottom: 1px solid #1f2925; border-top: 1px solid #1f2925; color: #b42318; font-size: 14px; font-weight: 900; letter-spacing: .08em; padding: 12px; text-align: center; text-transform: uppercase; }
          .grid { display: grid; gap: 22px; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); padding: 30px; }
          .candidate { border: 1.5px solid #1f2925; border-radius: 4px; display: grid; gap: 10px; min-height: 260px; padding: 14px; text-align: center; }
          .num { align-items: center; background: #075521; border-radius: 999px; color: #fff; display: inline-flex; font-family: monospace; font-weight: 900; height: 38px; justify-content: center; width: 38px; }
          .photo { align-items: center; background: #f4f7f5; border: 1px solid #dbe5df; display: flex; justify-content: center; min-height: 145px; overflow: hidden; }
          .photo img { height: 100%; object-fit: cover; width: 100%; }
          .blank .photo { background: #fff; border: 2px dashed #c5d0c9; color: #1f2925; font-size: 22px; font-weight: 900; text-transform: uppercase; }
          .role { color: #5f6f67; font-size: 10px; font-weight: 900; letter-spacing: .08em; text-transform: uppercase; }
          .name { color: #1f2925; font-size: 15px; font-weight: 900; line-height: 1.25; text-transform: uppercase; }
          .party { background: #e8f5ed; border: 1px solid #b7e4c7; color: #075521; font-size: 11px; font-weight: 900; padding: 8px; text-transform: uppercase; }
          .foot { background: #f4f7f5; border-top: 1px solid #1f2925; color: #5f6f67; display: flex; font-size: 11px; font-weight: 900; justify-content: space-between; padding: 14px 30px; text-transform: uppercase; }
          @media (max-width: 760px) { body { padding: 12px; } .head { grid-template-columns: 1fr; text-align: center; } .brand, .code { justify-items: center; justify-self: center; } .foot { flex-direction: column; gap: 8px; } }
        </style>
      </head>
      <body>
        ${Object.entries(agruparPorCargo(state.candidatos)).map(([cargo, items], index) => `
          <section class="sheet">
            <header class="head">
              <div class="brand"><span class="flag"><span></span><span></span><span></span></span><span>Elecciones Universitarias</span><span class="year">${new Date().getFullYear()}</span></div>
              <div class="title"><small>Voto por la formula de</small><h1>${escapeHtml(cargo)}</h1><span>${escapeHtml(state.eleccionActual?.nombre || 'Eleccion universitaria')} - Unicesar</span></div>
              <div class="code"><span class="bars"></span><strong>ABIS${String(index + 1).padStart(4, '0')}</strong></div>
            </header>
            <div class="instruction">Marque solo una opcion de su preferencia</div>
            <div class="grid">
              ${opcionesPreviewTarjeton(items).map((c) => `
                <article class="candidate${c.esVotoBlanco ? ' blank' : ''}">
                  <span class="num">${c.esVotoBlanco ? 'VB' : String(c.numeroCampania || '--').padStart(2, '0')}</span>
                  <div class="photo">${c.esVotoBlanco ? 'Voto en blanco' : (c.fotoUrl || c.foto_url ? `<img src="${escapeHtml(c.fotoUrl || c.foto_url)}" alt="Foto de ${escapeHtml(c.nombreCompleto)}">` : escapeHtml(iniciales(c)))}</div>
                  <span class="role">${escapeHtml(cargo)}</span>
                  <span class="name">${escapeHtml(c.nombreCompleto || c.nombre)}</span>
                  <span class="party">${escapeHtml(c.movimiento || c.partido || (c.esVotoBlanco ? 'Opcion oficial' : 'Universitario'))}</span>
                </article>
              `).join('')}
            </div>
            <footer class="foot"><span>Sistema Electoral - Unicesar</span><span>Documento oficial de votacion electronica</span></footer>
          </section>
        `).join('')}
      </body>
      </html>
    `);
    printWindow.document.close();
  }

  document.getElementById('btnAbrirTarjeton').addEventListener('click', abrirTarjetonCompletoVisual);
  document.getElementById('btnCerrarModalCandidato').addEventListener('click', () => cerrarModal(dom.modal));
  document.getElementById('btnCancelarCandidato').addEventListener('click', () => cerrarModal(dom.modal));

  dom.photoInput.addEventListener('change', () => {
    const file = dom.photoInput.files?.[0];
    if (!file) {
      dom.photoPreview.innerHTML = 'Sin foto';
      return;
    }
    const url = URL.createObjectURL(file);
    dom.photoPreview.innerHTML = `<img src="${url}" alt="Vista previa de foto">`;
  });

  document.getElementById('numeroCampania').addEventListener('input', () => {
    const payload = obtenerPayloadFormulario();
    limpiarErroresFormulario();
    const duplicado = validarNumeroCampana(payload.numeroCampania, state.eleccionId, state.editandoId);
    if (duplicado) {
      setFieldError('numeroCampania', `El número ${payload.numeroCampania} ya está asignado a ${nombreCompleto(duplicado)} en esta elección`);
    }
  });

  dom.form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = obtenerPayloadFormulario();
    if (!validarFormulario(payload)) return;
    try {
      await guardarCandidato(payload);
    } catch (error) {
      if (window.showToast) window.showToast(error.message, 'error');
    }
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      cerrarModal(dom.modal);
      cerrarModal(dom.modalDelete);
    }
  });

  dom.modal.addEventListener('click', (event) => {
    if (event.target === dom.modal) cerrarModal(dom.modal);
  });
  dom.modalDelete.addEventListener('click', (event) => {
    if (event.target === dom.modalDelete) cerrarModal(dom.modalDelete);
  });

  window.toggleCargo = toggleCargo;
  window.abrirModalNuevo = abrirModalNuevo;
  window.editarCandidato = editarCandidato;
  window.eliminarCandidato = eliminarCandidato;
  window.confirmarEliminarCandidato = confirmarEliminarCandidato;
  window.cerrarModalEliminarCandidato = () => cerrarModal(dom.modalDelete);
  window.aplicarFiltros = aplicarFiltros;
  window.cargarCandidatosPorEleccion = cargarCandidatosPorEleccion;
  window.renderizarGruposCargo = renderizarGruposCargo;
  window.renderizarCardCandidato = renderizarCardCandidato;
  window.validarNumeroCampana = validarNumeroCampana;
  window.guardarCandidato = guardarCandidato;
  window.renderizarTarjetonPreview = renderizarTarjetonPreview;
  window.cargarAuditoria = cargarAuditoria;

  iniciarActualizacionEnVivo();
  cargarElecciones();
})();
