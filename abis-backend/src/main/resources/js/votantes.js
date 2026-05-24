(function initVotersPage() {
  const state = {
    voters: [],
    filtered: [],
    page: 1,
    pageSize: 10,
    filters: {
      search: '',
      state: '',
      role: '',
      place: '',
      bio: '',
      qr: ''
    }
  };

  const roleNames = {
    1: 'Estudiante',
    2: 'Docente',
    3: 'Egresado',
    4: 'Administrativo'
  };

  const placeNames = {
    1: 'Puesto 1',
    2: 'Puesto 2',
    3: 'Puesto 3'
  };

  const token = localStorage.getItem('abis_token');
  const headers = token ? { Authorization: 'Bearer ' + token } : {};

  function normalize(value) {
    return String(value || '').trim();
  }

  function normalizeUpper(value) {
    return normalize(value).toUpperCase();
  }

  function fullName(voter) {
    return [
      voter.primer_nombre,
      voter.segundo_nombre,
      voter.primer_apellido,
      voter.segundo_apellido
    ].filter(Boolean).join(' ').replace(/\s+/g, ' ').trim() || 'Sin nombre registrado';
  }

  function initials(name) {
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('') || 'VT';
  }

  function roleName(voter) {
    return roleNames[voter.rol_id] || `Rol ${voter.rol_id || '--'}`;
  }

  function placeName(voter) {
    return placeNames[voter.puesto_id] || `Puesto ${voter.puesto_id || '--'}`;
  }

  function isBiometric(voter) {
    return Boolean(voter.biometrico);
  }

  function stateLabel(voter) {
    return normalizeUpper(voter.estado_voto) || 'PENDIENTE';
  }

  function percent(value, total) {
    if (!total) return '0%';
    return `${((value / total) * 100).toFixed(1).replace('.', ',')}%`;
  }

  function number(value) {
    return new Intl.NumberFormat('es-CO').format(value || 0);
  }

  function escapeHtml(value) {
    return String(value || '').replace(/[&<>"']/g, (char) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }[char]));
  }

  function escapeAttr(value) {
    return escapeHtml(value).replace(/`/g, '&#96;');
  }

  function roleClass(voter) {
    const role = roleName(voter).toLowerCase();
    if (role.includes('estudiante')) return 'badge-student';
    if (role.includes('docente')) return 'badge-teacher';
    if (role.includes('admin')) return 'badge-admin';
    if (role.includes('egres')) return 'badge-grad';
    return 'badge-neutral';
  }

  function stateClass(voter) {
    const status = stateLabel(voter);
    if (status === 'EJERCIDO') return 'badge-voted';
    if (status === 'INHABILITADO') return 'badge-disabled';
    return 'badge-pending';
  }

  function lastActivity(voter) {
    const status = stateLabel(voter);
    if (status === 'EJERCIDO') return { title: 'Votacion registrada', detail: 'Registro electoral cerrado' };
    if (status === 'INHABILITADO') return { title: 'Votante inhabilitado', detail: 'Acceso a tarjeton bloqueado' };
    if (isBiometric(voter)) return { title: 'Enrolamiento biometrico', detail: 'Plantilla asociada al censo' };
    if (voter.qr_cedula) return { title: 'Segunda llave registrada', detail: 'QR PDF417 disponible' };
    return { title: 'Registro inicial', detail: 'Pendiente de biometria' };
  }

  function renderKpis() {
    const total = state.voters.length;
    const pending = state.voters.filter((v) => stateLabel(v) === 'PENDIENTE').length;
    const voted = state.voters.filter((v) => stateLabel(v) === 'EJERCIDO').length;
    const disabled = state.voters.filter((v) => stateLabel(v) === 'INHABILITADO').length;
    const bio = state.voters.filter(isBiometric).length;

    document.getElementById('voters-kpis').innerHTML = [
      ['groups', 'TOTAL VOTANTES', number(total), '100% del censo', 'green', ''],
      ['check_box', 'PENDIENTES', number(pending), `${percent(pending, total)} del total`, 'light-green', 'voter-kpi-green'],
      ['how_to_vote', 'EJERCIDOS', number(voted), `${percent(voted, total)} del total`, 'blue', 'voter-kpi-blue'],
      ['person_cancel', 'INHABILITADOS', number(disabled), `${percent(disabled, total)} del total`, 'red', 'voter-kpi-red'],
      ['fingerprint', 'CON BIOMETRICO', number(bio), `${percent(bio, total)} del total`, 'purple', 'voter-kpi-purple']
    ].map(([icon, label, value, note, iconClass, cardClass]) => `
      <article class="voter-kpi ${cardClass}">
        <div class="voter-kpi-icon ${iconClass}">
          <span class="material-symbols-outlined">${icon}</span>
        </div>
        <div>
          <p class="voter-kpi-label">${label}</p>
          <p class="voter-kpi-value">${value}</p>
          <p class="voter-kpi-note">${note}</p>
        </div>
      </article>
    `).join('');
  }

  function populateFilters() {
    const stateSelect = document.getElementById('voters-filter-state');
    const roleSelect = document.getElementById('voters-filter-role');
    const placeSelect = document.getElementById('voters-filter-place');

    const states = [...new Set(state.voters.map(stateLabel))].sort();
    const roles = [...new Set(state.voters.map(roleName))].sort();
    const places = [...new Set(state.voters.map(placeName))].sort();

    stateSelect.innerHTML = '<option value="">Todos</option>' + states.map((item) => `<option value="${escapeHtml(item)}">${escapeHtml(item)}</option>`).join('');
    roleSelect.innerHTML = '<option value="">Todos</option>' + roles.map((item) => `<option value="${escapeHtml(item)}">${escapeHtml(item)}</option>`).join('');
    placeSelect.innerHTML = '<option value="">Todos</option>' + places.map((item) => `<option value="${escapeHtml(item)}">${escapeHtml(item)}</option>`).join('');
  }

  function applyFilters() {
    state.filters.search = normalize(document.getElementById('voters-search').value).toLowerCase();
    state.filters.state = document.getElementById('voters-filter-state').value;
    state.filters.role = document.getElementById('voters-filter-role').value;
    state.filters.place = document.getElementById('voters-filter-place').value;
    state.filters.bio = document.getElementById('voters-filter-bio').value;
    state.filters.qr = document.getElementById('voters-filter-qr').value;

    state.filtered = state.voters.filter((voter) => {
      const searchable = [
        voter.identificacion,
        voter.correo,
        fullName(voter),
        roleName(voter),
        placeName(voter)
      ].join(' ').toLowerCase();

      if (state.filters.search && !searchable.includes(state.filters.search)) return false;
      if (state.filters.state && stateLabel(voter) !== state.filters.state) return false;
      if (state.filters.role && roleName(voter) !== state.filters.role) return false;
      if (state.filters.place && placeName(voter) !== state.filters.place) return false;
      if (state.filters.bio === 'enrolado' && !isBiometric(voter)) return false;
      if (state.filters.bio === 'no_enrolado' && isBiometric(voter)) return false;
      if (state.filters.qr === 'registrado' && !voter.qr_cedula) return false;
      if (state.filters.qr === 'pendiente' && voter.qr_cedula) return false;
      return true;
    });

    state.page = 1;
    renderTable();
  }

  function renderTable() {
    const tbody = document.getElementById('voters-table-body');
    const total = state.filtered.length;
    const totalPages = Math.max(1, Math.ceil(total / state.pageSize));
    state.page = Math.min(state.page, totalPages);

    const start = (state.page - 1) * state.pageSize;
    const rows = state.filtered.slice(start, start + state.pageSize);

    if (!rows.length) {
      tbody.innerHTML = '<tr><td colspan="9" class="voters-empty">No hay votantes que coincidan con los filtros aplicados.</td></tr>';
    } else {
      tbody.innerHTML = rows.map((voter) => {
        const name = fullName(voter);
        const activity = lastActivity(voter);
        const bio = isBiometric(voter);
        return `
          <tr>
            <td><div class="voter-avatar-lite">${escapeHtml(initials(name))}</div></td>
            <td>
              <div class="voter-id">${escapeHtml(voter.identificacion || '--')}</div>
              <div class="voter-subcopy">${voter.qr_cedula ? 'QR registrado' : 'QR pendiente'}</div>
            </td>
            <td class="voter-name-cell">
              <div class="voter-name">${escapeHtml(name)}</div>
              <div class="voter-email">${escapeHtml(voter.correo || 'Correo no registrado')}</div>
            </td>
            <td><span class="voter-badge ${roleClass(voter)}">${escapeHtml(roleName(voter))}</span></td>
            <td>
              <div class="voter-name">${escapeHtml(placeName(voter))}</div>
              <div class="voter-subcopy">ID puesto ${escapeHtml(voter.puesto_id || '--')}</div>
            </td>
            <td>
              <span class="voter-badge ${bio ? 'badge-bio' : 'badge-no-bio'}">
                <span class="material-symbols-outlined" style="font-size:15px">fingerprint</span>
                ${bio ? 'Enrolado' : 'No enrolado'}
              </span>
            </td>
            <td><span class="voter-badge ${stateClass(voter)}">${escapeHtml(stateLabel(voter))}</span></td>
            <td>
              <div class="voter-name">${escapeHtml(activity.title)}</div>
              <div class="voter-subcopy">${escapeHtml(activity.detail)}</div>
            </td>
            <td class="voter-actions">
              <button class="voter-menu-btn" data-voter-menu="${escapeHtml(voter.identificacion || '')}">
                <span class="material-symbols-outlined">more_vert</span>
              </button>
            </td>
          </tr>
        `;
      }).join('');
    }

    const end = total ? Math.min(start + rows.length, total) : 0;
    document.getElementById('voters-page-summary').textContent = total
      ? `Mostrando ${start + 1} a ${end} de ${number(total)} votantes`
      : 'Sin registros para mostrar';

    renderPagination(totalPages);
    bindRowMenus();
  }

  function renderPagination(totalPages) {
    const container = document.getElementById('voters-pagination');
    const pages = [];
    pages.push(`<button class="voters-page-btn" data-page="${Math.max(1, state.page - 1)}"><span class="material-symbols-outlined" style="font-size:18px">chevron_left</span></button>`);
    for (let page = 1; page <= totalPages; page += 1) {
      if (page === 1 || page === totalPages || Math.abs(page - state.page) <= 1) {
        pages.push(`<button class="voters-page-btn ${page === state.page ? 'active' : ''}" data-page="${page}">${page}</button>`);
      } else if (Math.abs(page - state.page) === 2) {
        pages.push('<span class="voters-page-btn">...</span>');
      }
    }
    pages.push(`<button class="voters-page-btn" data-page="${Math.min(totalPages, state.page + 1)}"><span class="material-symbols-outlined" style="font-size:18px">chevron_right</span></button>`);
    container.innerHTML = pages.join('');
    container.querySelectorAll('[data-page]').forEach((button) => {
      button.addEventListener('click', () => {
        state.page = Number(button.dataset.page);
        renderTable();
      });
    });
  }

  function bindRowMenus() {
    document.querySelectorAll('[data-voter-menu]').forEach((button) => {
      button.addEventListener('click', (event) => {
        event.stopPropagation();
        const voter = state.voters.find((item) => item.identificacion === button.dataset.voterMenu);
        openPopover(button, voter);
      });
    });
  }

  function openPopover(anchor, voter) {
    closePopover();
    if (!voter) return;
    const rect = anchor.getBoundingClientRect();
    const popover = document.createElement('div');
    popover.className = 'voter-popover';
    popover.id = 'voter-popover';
    popover.innerHTML = `
      <button data-action="details"><span class="material-symbols-outlined">visibility</span>Ver detalles</button>
      <button data-action="edit"><span class="material-symbols-outlined">edit</span>Editar datos</button>
      <button data-action="role"><span class="material-symbols-outlined">groups</span>Cambiar rol</button>
      <button data-action="place"><span class="material-symbols-outlined">location_on</span>Cambiar puesto</button>
      <button data-action="bio"><span class="material-symbols-outlined">fingerprint</span>Re-enrolar</button>
      <button data-action="audit"><span class="material-symbols-outlined">history</span>Auditoria</button>
      <button data-action="disable" class="danger"><span class="material-symbols-outlined">block</span>Inhabilitar</button>
    `;
    document.body.appendChild(popover);
    positionPopover(anchor, popover);
    popover.querySelectorAll('button').forEach((button) => {
      button.addEventListener('click', () => {
        closePopover();
        handleAction(button.dataset.action, voter);
      });
    });
    setTimeout(() => document.addEventListener('click', closePopover, { once: true }), 0);
  }

  function positionPopover(anchor, popover) {
    const margin = 12;
    const gap = 8;
    const rect = anchor.getBoundingClientRect();
    const menuRect = popover.getBoundingClientRect();
    const spaceBelow = window.innerHeight - rect.bottom;
    const spaceAbove = rect.top;
    const openUp = spaceBelow < menuRect.height + gap + margin && spaceAbove > spaceBelow;

    const left = Math.min(
      Math.max(margin, rect.right - menuRect.width),
      window.innerWidth - menuRect.width - margin
    );
    const top = openUp
      ? Math.max(margin, rect.top - menuRect.height - gap)
      : Math.min(rect.bottom + gap, window.innerHeight - menuRect.height - margin);

    popover.style.left = `${left}px`;
    popover.style.top = `${top}px`;
    popover.classList.toggle('dropup', openUp);
  }

  function closePopover() {
    const existing = document.getElementById('voter-popover');
    if (existing) existing.remove();
  }

  function handleAction(action, voter) {
    if (['details', 'edit', 'bio', 'audit'].includes(action)) {
      openDrawer(voter, action);
      return;
    }
    const labels = {
      role: 'Cambio de rol disponible en la siguiente iteracion.',
      place: 'Cambio de puesto disponible en la siguiente iteracion.',
      disable: 'Inhabilitacion disponible cuando se conecte auditoria transaccional.'
    };
    alert(labels[action] || 'Accion no disponible.');
  }

  function openDrawer(voter, action) {
    const drawer = document.getElementById('voter-drawer');
    const content = document.getElementById('voter-drawer-content');
    const name = fullName(voter);
    const editable = action === 'edit';
    const photo = voter.foto_url
      ? `<img src="${escapeHtml(voter.foto_url)}" alt="Foto de ${escapeHtml(name)}" loading="lazy">`
      : `<div class="drawer-initials">${escapeHtml(initials(name))}</div>`;
    const roleOptions = Object.entries(roleNames).map(([id, label]) =>
      `<option value="${escapeAttr(id)}" ${Number(voter.rol_id) === Number(id) ? 'selected' : ''}>${escapeHtml(label)}</option>`
    ).join('');
    const placeOptions = Object.entries(placeNames).map(([id, label]) =>
      `<option value="${escapeAttr(id)}" ${Number(voter.puesto_id) === Number(id) ? 'selected' : ''}>${escapeHtml(label)}</option>`
    ).join('');
    content.innerHTML = `
      <div class="voter-modal-layout">
        <aside class="voter-modal-profile">
          <button class="drawer-back" type="button" data-close-voter-modal>
            <span class="material-symbols-outlined">arrow_back</span>
          </button>
          <div class="drawer-photo">${photo}</div>
          <h3 class="drawer-name">${escapeHtml(name)}</h3>
          <p class="drawer-role">${escapeHtml(roleName(voter))}</p>
          <div class="drawer-status-pill ${stateClass(voter)}">
            <span></span>${escapeHtml(stateLabel(voter))}
          </div>
        </aside>

        <form class="voter-modal-form" id="voter-edit-form">
          <div class="voter-modal-heading">
            <div>
              <p class="voters-field-label">Ficha del votante</p>
              <h3>Detalle operativo</h3>
            </div>
            <button class="drawer-close in-content" type="button" data-close-voter-modal>
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>

          <div class="voter-form-grid">
            <label>
              <span class="drawer-label">Primer nombre</span>
              <input name="primer_nombre" value="${escapeAttr(voter.primer_nombre || '')}" ${editable ? '' : 'readonly'}>
            </label>
            <label>
              <span class="drawer-label">Segundo nombre</span>
              <input name="segundo_nombre" value="${escapeAttr(voter.segundo_nombre || '')}" ${editable ? '' : 'readonly'}>
            </label>
            <label>
              <span class="drawer-label">Primer apellido</span>
              <input name="primer_apellido" value="${escapeAttr(voter.primer_apellido || '')}" ${editable ? '' : 'readonly'}>
            </label>
            <label>
              <span class="drawer-label">Segundo apellido</span>
              <input name="segundo_apellido" value="${escapeAttr(voter.segundo_apellido || '')}" ${editable ? '' : 'readonly'}>
            </label>
            <label>
              <span class="drawer-label">Identificacion</span>
              <input name="identificacion" value="${escapeAttr(voter.identificacion || '')}" readonly>
            </label>
            <label class="wide">
              <span class="drawer-label">Correo</span>
              <input name="correo" type="email" value="${escapeAttr(voter.correo || '')}" ${editable ? '' : 'readonly'}>
            </label>
            <label>
              <span class="drawer-label">Rol</span>
              <select name="rol_id" ${editable ? '' : 'disabled'}>${roleOptions}</select>
            </label>
            <label>
              <span class="drawer-label">Puesto</span>
              <select name="puesto_id" ${editable ? '' : 'disabled'}>${placeOptions}</select>
            </label>
            <div class="drawer-field compact"><div class="drawer-label">Estado</div><div class="drawer-value">${escapeHtml(stateLabel(voter))}</div></div>
            <div class="drawer-field compact"><div class="drawer-label">Biometrico</div><div class="drawer-value">${isBiometric(voter) ? 'Enrolado' : 'No enrolado'}</div></div>
            <div class="drawer-field compact wide"><div class="drawer-label">QR cedula</div><div class="drawer-value">${voter.qr_cedula ? 'Registrado' : 'No registrado'}</div></div>
          </div>

          <div class="voter-modal-error" id="voter-modal-error"></div>
          <div class="voter-modal-actions">
            ${editable
              ? '<button class="voters-btn voters-btn-ghost" type="button" data-close-voter-modal>Cancelar</button><button class="voters-btn voters-btn-primary" type="submit">Guardar cambios</button>'
              : '<button class="voters-btn voters-btn-ghost" type="button" id="voter-modal-edit">Editar</button><button class="voters-btn voters-btn-primary" type="button" id="voter-modal-audit"><span class="material-symbols-outlined">history</span>Ver auditoria</button>'}
          </div>
        </form>
      </div>
    `;
    content.querySelectorAll('[data-close-voter-modal]').forEach((button) => button.addEventListener('click', closeDrawer));
    content.querySelector('#voter-modal-edit')?.addEventListener('click', () => openDrawer(voter, 'edit'));
    content.querySelector('#voter-modal-audit')?.addEventListener('click', () => {
      closeDrawer();
      document.querySelector('.voters-audit-card')?.scrollIntoView({ behavior: 'smooth' });
    });
    content.querySelector('#voter-edit-form')?.addEventListener('submit', (event) => saveVoter(event, voter));
    drawer.classList.add('open');
    drawer.setAttribute('aria-hidden', 'false');
  }

  async function saveVoter(event, voter) {
    event.preventDefault();
    const form = event.currentTarget;
    const error = document.getElementById('voter-modal-error');
    const submit = form.querySelector('button[type="submit"]');
    const payload = Object.fromEntries(new FormData(form).entries());
    if (!payload.primer_nombre?.trim() || !payload.primer_apellido?.trim()) {
      error.textContent = 'Primer nombre y primer apellido son obligatorios.';
      error.classList.add('visible');
      return;
    }

    try {
      error.textContent = '';
      error.classList.remove('visible');
      if (submit) {
        submit.disabled = true;
        submit.textContent = 'Guardando...';
      }
      const response = await fetch(`/api/votantes/${encodeURIComponent(voter.identificacion)}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          ...headers
        },
        body: JSON.stringify({
          primer_nombre: payload.primer_nombre.trim(),
          segundo_nombre: payload.segundo_nombre.trim(),
          primer_apellido: payload.primer_apellido.trim(),
          segundo_apellido: payload.segundo_apellido.trim(),
          correo: payload.correo.trim(),
          rol_id: Number(payload.rol_id),
          puesto_id: Number(payload.puesto_id)
        })
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body.error || body.message || `HTTP ${response.status}`);
      }
      closeDrawer();
      await loadVoters();
    } catch (saveError) {
      error.textContent = saveError.message || 'No fue posible guardar los cambios.';
      error.classList.add('visible');
    } finally {
      if (submit) {
        submit.disabled = false;
        submit.textContent = 'Guardar cambios';
      }
    }
  }

  function closeDrawer() {
    const drawer = document.getElementById('voter-drawer');
    drawer.classList.remove('open');
    drawer.setAttribute('aria-hidden', 'true');
  }

  async function renderAudit(limit = 6) {
    const target = document.getElementById('voters-audit-list');
    try {
      const response = await fetch(`/api/auditoria/reciente?limit=${encodeURIComponent(limit)}`, { headers });
      if (!response.ok) throw new Error('audit unavailable');
      const audit = await response.json();
      const rows = Array.isArray(audit?.data) ? audit.data : audit;
      if (Array.isArray(rows) && rows.length) {
        target.innerHTML = rows.slice(0, limit).map((item) => auditItem({
          action: item.accion || item.campo_modificado || 'Edicion de datos',
          name: item.identificacion || 'Votante',
          meta: item.fechaHora || item.fecha_hora || 'Fecha no disponible',
          detail: item.motivo || `${item.valorAnterior || item.valor_anterior || ''} ${item.valorNuevo || item.valor_nuevo ? '-> ' + (item.valorNuevo || item.valor_nuevo) : ''}`.trim(),
          type: auditType(item.accion)
        })).join('');
        return;
      }
      throw new Error('empty audit');
    } catch (error) {
      target.innerHTML = [
        { action: 'Edicion de datos', name: 'Censo electoral', meta: 'Actividad administrativa', detail: 'Correccion de datos personales', type: 'edit' },
        { action: 'Re-enrolamiento', name: 'Modulo biometrico', meta: 'Control de identidad', detail: 'Nueva plantilla biometrica', type: 'bio' },
        { action: 'Cambio de rol', name: 'Gestion de votantes', meta: 'Trazabilidad pendiente', detail: 'Rol actualizado por administrador', type: 'role' },
        { action: 'Inhabilitacion', name: 'Control electoral', meta: 'Bloqueo operativo', detail: 'Votante marcado como inhabilitado', type: 'warn' }
      ].map(auditItem).join('');
    }
  }

  function auditType(action) {
    const value = normalizeUpper(action);
    if (value.includes('ENROL')) return 'bio';
    if (value.includes('INHABIL')) return 'warn';
    if (value.includes('ROL')) return 'role';
    return 'edit';
  }

  function auditItem(item) {
    const icon = {
      edit: 'edit_square',
      bio: 'fingerprint',
      warn: 'block',
      role: 'groups'
    }[item.type] || 'history';
    return `
      <article class="audit-item">
        <div class="audit-icon ${item.type}"><span class="material-symbols-outlined" style="font-size:18px">${icon}</span></div>
        <div>
          <div class="audit-action">${escapeHtml(item.action)}</div>
          <div class="audit-name">${escapeHtml(item.name)}</div>
          <div class="audit-meta">${escapeHtml(item.meta)}</div>
          <div class="audit-detail">${escapeHtml(item.detail || 'Sin detalle adicional')}</div>
        </div>
      </article>
    `;
  }

  function exportCsv() {
    const headers = ['identificacion', 'nombre', 'correo', 'rol', 'puesto', 'estado', 'biometrico', 'qr_cedula'];
    const rows = state.filtered.map((voter) => [
      voter.identificacion,
      fullName(voter),
      voter.correo,
      roleName(voter),
      placeName(voter),
      stateLabel(voter),
      isBiometric(voter) ? 'SI' : 'NO',
      voter.qr_cedula ? 'SI' : 'NO'
    ]);
    const csv = [headers, ...rows].map((row) => row.map((cell) => `"${String(cell || '').replace(/"/g, '""')}"`).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'censo-electoral.csv';
    link.click();
    URL.revokeObjectURL(url);
  }

  async function loadVoters() {
    const tbody = document.getElementById('voters-table-body');
    tbody.innerHTML = '<tr><td colspan="9" class="voters-empty">Cargando censo electoral...</td></tr>';
    try {
      const response = await fetch('/api/votantes', { headers });
      if (!response.ok) throw new Error('No fue posible cargar votantes');
      state.voters = await response.json();
      state.filtered = [...state.voters];
      renderKpis();
      populateFilters();
      renderTable();
      renderAudit();
    } catch (error) {
      document.getElementById('voters-kpis').innerHTML = '';
      tbody.innerHTML = '<tr><td colspan="9" class="voters-empty">No fue posible cargar el censo electoral.</td></tr>';
      document.getElementById('voters-page-summary').textContent = 'Error cargando votantes';
      renderAudit();
    }
  }

  document.getElementById('voters-apply').addEventListener('click', applyFilters);
  document.getElementById('voters-clear').addEventListener('click', () => {
    ['voters-search', 'voters-filter-state', 'voters-filter-role', 'voters-filter-place', 'voters-filter-bio', 'voters-filter-qr'].forEach((id) => {
      document.getElementById(id).value = '';
    });
    applyFilters();
  });
  document.getElementById('voters-search').addEventListener('input', () => {
    clearTimeout(window.__votersSearchTimer);
    window.__votersSearchTimer = setTimeout(applyFilters, 180);
  });
  document.getElementById('voters-export').addEventListener('click', exportCsv);
  document.getElementById('voters-import').addEventListener('click', () => document.getElementById('voters-import-input').click());
  document.getElementById('voters-import-input').addEventListener('change', () => alert('Importacion masiva pendiente de endpoint backend.'));
  document.getElementById('voters-new').addEventListener('click', () => { window.location.href = '/pages/registro/index.html'; });
  document.getElementById('voters-audit-button').addEventListener('click', async () => {
    await renderAudit(50);
    document.querySelector('.voters-audit-card')?.scrollIntoView({ behavior: 'smooth' });
  });
  document.getElementById('voters-audit-link').addEventListener('click', () => renderAudit(50));
  document.getElementById('voter-drawer-close').addEventListener('click', closeDrawer);
  document.getElementById('voter-drawer').addEventListener('click', (event) => {
    if (event.target.id === 'voter-drawer') closeDrawer();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      closePopover();
      closeDrawer();
    }
  });

  loadVoters();
})();

