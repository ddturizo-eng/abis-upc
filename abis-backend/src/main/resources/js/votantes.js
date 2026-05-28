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

  const placeNames = {};

  async function loadPuestos() {
    try {
      const response = await fetch('/api/puestos', { headers });
      if (response.ok) {
        const data = await response.json();
        const puestos = Array.isArray(data) ? data : (data.data || []);
        puestos.forEach(p => {
          placeNames[p.id || p.id_puesto] = p.nombre_puesto || p.nombrePuesto || p.nombre || ('Puesto ' + (p.id || p.id_puesto));
        });
      }
    } catch (e) {
      console.warn('No se pudieron cargar los puestos de votacion:', e);
    }
  }

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

  let kpiStats = { total: 0, votaron: 0, pendientes: 0, inhabilitados: 0 };

  async function loadKpiStats() {
    try {
      const response = await fetch('/api/admin/estadisticas-votantes', { headers });
      if (response.ok) {
        const data = await response.json();
        kpiStats = {
          total: Number(data.total || 0),
          votaron: Number(data.votaron || data.ejercidos || 0),
          pendientes: Number(data.pendientes || 0),
          inhabilitados: Number(data.inhabilitados || 0)
        };
        return;
      }
    } catch (e) { /* fallback a calculo local */ }
    kpiStats = {
      total: state.voters.length,
      votaron: 0,
      pendientes: state.voters.filter(v => stateLabel(v) === 'PENDIENTE').length,
      inhabilitados: state.voters.filter(v => stateLabel(v) === 'INHABILITADO').length
    };
  }

  function renderKpis() {
    const total = kpiStats.total || state.voters.length;
    const pending = kpiStats.pendientes;
    const voted = kpiStats.votaron;
    const disabled = kpiStats.inhabilitados;
    const bio = state.voters.filter(isBiometric).length;

    document.getElementById('voters-kpis').innerHTML = [
      ['groups', 'TOTAL VOTANTES', number(total), '100% del censo', 'green', 'voter-kpi-green voter-kpi-total'],
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
          <tr data-voter-ident="${escapeAttr(voter.identificacion || '')}">
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
            <td class="col-activity">
              <div class="voter-name">${escapeHtml(activity.title)}</div>
              <div class="voter-subcopy">${escapeHtml(activity.detail)}</div>
            </td>
            <td class="voter-actions">
              <div class="voter-action-icons">
                <button class="action-icon-btn" data-voter-ident="${escapeAttr(voter.identificacion || '')}" data-action="details" title="Ver detalles">
                  <span class="material-symbols-outlined" style="font-size:17px">visibility</span>
                  <span class="tooltip">Ver detalles</span>
                </button>
                <button class="action-icon-btn" data-voter-ident="${escapeAttr(voter.identificacion || '')}" data-action="edit" title="Editar">
                  <span class="material-symbols-outlined" style="font-size:17px">edit</span>
                  <span class="tooltip">Editar</span>
                </button>
                <button class="voter-menu-btn" data-voter-menu="${escapeAttr(voter.identificacion || '')}">
                  <span class="material-symbols-outlined">more_vert</span>
                </button>
              </div>
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

    document.querySelectorAll('.action-icon-btn').forEach((button) => {
      button.addEventListener('click', (event) => {
        event.stopPropagation();
        const voter = state.voters.find((item) => item.identificacion === button.dataset.voterIdent);
        const action = button.dataset.action || 'details';
        handleAction(action, voter);
      });
    });

    document.querySelectorAll('tr[data-voter-ident]').forEach((row) => {
      row.addEventListener('click', (event) => {
        if (event.target.closest('button') || event.target.closest('input')) return;
        const voter = state.voters.find((item) => item.identificacion === row.dataset.voterIdent);
        if (voter) openDrawer(voter, 'details');
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
    const disabled = voter.estado_voto === 'INHABILITADO';
    popover.innerHTML = `
      <button data-action="details"><span class="material-symbols-outlined">visibility</span>Ver detalles</button>
      <button data-action="edit"><span class="material-symbols-outlined">edit</span>Editar datos</button>
      <button data-action="role"><span class="material-symbols-outlined">groups</span>Cambiar rol</button>
      <button data-action="place"><span class="material-symbols-outlined">location_on</span>Cambiar puesto</button>
      <button data-action="bio"><span class="material-symbols-outlined">fingerprint</span>Re-enrolar</button>
      <button data-action="audit"><span class="material-symbols-outlined">history</span>Auditoria</button>
      ${disabled
        ? '<button data-action="enable"><span class="material-symbols-outlined">check_circle</span>Habilitar</button>'
        : '<button data-action="disable" class="danger"><span class="material-symbols-outlined">block</span>Inhabilitar</button>'}
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

  function openAuditSlideover() {
    closeAuditFullModal();
    document.getElementById('audit-overlay').classList.add('open');
    document.getElementById('audit-slideover').classList.add('open');
    renderAudit(50);
  }

  function closeAuditSlideover() {
    document.getElementById('audit-overlay').classList.remove('open');
    document.getElementById('audit-slideover').classList.remove('open');
  }

  function openAuditFullModal() {
    closeAuditSlideover();
    const modal = document.getElementById('audit-full-modal');
    modal.classList.add('open');
    renderAuditFull();
  }

  function closeAuditFullModal() {
    document.getElementById('audit-full-modal').classList.remove('open');
  }

  let auditFullAll = [];
  let auditFullFilter = '';

  async function renderAuditFull() {
    const tbody = document.getElementById('audit-full-tbody');
    try {
      const response = await fetch(`/api/auditoria/reciente?limit=300`, { headers });
      if (!response.ok) throw new Error('audit unavailable');
      const data = await response.json();
      auditFullAll = Array.isArray(data?.data) ? data.data : Array.isArray(data) ? data : [];
    } catch (e) {
      auditFullAll = [];
    }
    renderAuditFullFiltered();
  }

  function renderAuditFullFiltered() {
    const tbody = document.getElementById('audit-full-tbody');
    const search = document.getElementById('audit-full-search-input')?.value?.toLowerCase() || '';
    const rows = auditFullAll.filter(item => {
      const matchFilter = !auditFullFilter || (item.accion || item.campo_modificado || '').includes(auditFullFilter);
      const matchSearch = !search || [item.identificacion || '', item.accion || '', item.campo_modificado || '', item.motivo || ''].join(' ').toLowerCase().includes(search);
      return matchFilter && matchSearch;
    });
    if (!rows.length) {
      tbody.innerHTML = '<tr><td colspan="5" style="padding:32px;text-align:center;color:var(--gray-500);font-size:0.86rem">Sin registros de auditoría para mostrar.</td></tr>';
      return;
    }
    tbody.innerHTML = rows.map(item => {
      const action = item.accion || item.campo_modificado || 'Edicion de datos';
      const type = auditType(action);
      return `
        <tr>
          <td style="white-space:nowrap;color:var(--gray-500)">${escapeHtml(item.fechaHora || item.fecha_hora || '--')}</td>
          <td><span class="audit-type-badge ${type}">${escapeHtml(action)}</span></td>
          <td><span style="font-family:var(--font-mono);font-size:0.78rem">${escapeHtml(item.identificacion || '--')}</span></td>
          <td>${escapeHtml(item.motivo || `${item.valorAnterior || item.valor_anterior || ''} ${item.valorNuevo || item.valor_nuevo ? '→ ' + (item.valorNuevo || item.valor_nuevo) : ''}`.trim() || 'Sin detalle')}</td>
          <td><span style="color:var(--gray-500);font-size:0.78rem">${escapeHtml(item.idAdmin || item.id_admin || 'Sistema')}</span></td>
        </tr>
      `;
    }).join('');
  }

  function initAuditFullModal() {
    document.getElementById('audit-full-close-btn')?.addEventListener('click', closeAuditFullModal);
    document.getElementById('audit-full-modal')?.addEventListener('click', e => {
      if (e.target.id === 'audit-full-modal') closeAuditFullModal();
    });

    document.getElementById('audit-full-search-input')?.addEventListener('input', renderAuditFullFiltered);

    document.querySelectorAll('.audit-filter-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        document.querySelectorAll('.audit-filter-chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        auditFullFilter = chip.dataset.filter || '';
        renderAuditFullFiltered();
      });
    });
  }

  function initColumnToggle() {
    const btn = document.getElementById('col-toggle-btn');
    const dropdown = document.getElementById('col-toggle-dropdown');
    const qrCheck = document.getElementById('col-qr-check');
    const activityCheck = document.getElementById('col-activity-check');

    if (!btn || !dropdown) return;

    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      dropdown.classList.toggle('open');
    });

    document.addEventListener('click', () => dropdown.classList.remove('open'));

    qrCheck.addEventListener('change', () => {
      const col = document.querySelector('.col-qr');
      if (col) col.classList.toggle('visible', qrCheck.checked);
    });

    activityCheck.addEventListener('change', () => {
      const cols = document.querySelectorAll('.col-activity');
      cols.forEach(c => c.classList.toggle('visible', activityCheck.checked));
    });
  }

  function handleAction(action, voter) {
    if (['details', 'edit', 'bio', 'audit'].includes(action)) {
      openDrawer(voter, action);
      return;
    }
    if (action === 'role' || action === 'place') {
      openDrawer(voter, 'edit');
      return;
    }
    if (action === 'disable') {
      inhabilitarVotante(voter);
      return;
    }
    if (action === 'enable') {
      habilitarVotante(voter);
      return;
    }
  }

  async function inhabilitarVotante(voter) {
    const motivo = prompt(`Motivo para inhabilitar a ${fullName(voter)}:`, 'Inhabilitacion administrativa');
    if (!motivo) return;
    try {
      await API.request(`/api/votantes/${encodeURIComponent(voter.identificacion)}/inhabilitar`, {
        method: 'PUT',
        body: { motivo }
      });
      closeDrawer();
      await loadVoters();
    } catch (error) {
      showToast('Error al inhabilitar: ' + (error.message || 'Error desconocido'), 'error');
    }
  }

  async function habilitarVotante(voter) {
    const motivo = prompt(`Motivo para habilitar a ${fullName(voter)}:`, 'Habilitacion administrativa');
    if (!motivo) return;
    try {
      await API.request(`/api/votantes/${encodeURIComponent(voter.identificacion)}/habilitar`, {
        method: 'PUT',
        body: { motivo }
      });
      closeDrawer();
      await loadVoters();
    } catch (error) {
      showToast('Error al habilitar: ' + (error.message || 'Error desconocido'), 'error');
    }
  }

  function openDrawer(voter, action) {
    if (action === 'bio') {
      abrirModalReEnrolamiento(voter);
      return;
    }
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
          <div class="drawer-photo" id="drawer-photo">${photo}</div>
          ${editable ? `
            <label class="drawer-photo-upload" id="drawer-photo-upload-label">
              <span class="material-symbols-outlined" style="font-size:16px">add_a_photo</span>
              Cambiar foto
              <input type="file" accept="image/*" id="drawer-photo-input" style="display:none" onchange="cambiarFotoVotante(event, '${escapeAttr(voter.identificacion)}')">
            </label>
          ` : ''}
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
            <label>
              <span class="drawer-label">Fecha de nacimiento</span>
              <input name="fecha_nacimiento" type="date" value="${escapeAttr(voter.fecha_nacimiento || '')}" ${editable ? '' : 'readonly'}>
            </label>
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
    const submit = form.querySelector('button[type="submit"]');
    const payload = Object.fromEntries(new FormData(form).entries());
    if (!payload.primer_nombre?.trim() || !payload.primer_apellido?.trim()) {
      if (window.showToast) window.showToast('Primer nombre y primer apellido son obligatorios.', 'error');
      return;
    }

    try {
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
          puesto_id: Number(payload.puesto_id),
          fecha_nacimiento: payload.fecha_nacimiento || null
        })
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body.error || body.message || `HTTP ${response.status}`);
      }
      closeDrawer();
      await loadVoters();
    } catch (saveError) {
      if (window.showToast) window.showToast(saveError.message || 'No fue posible guardar los cambios.', 'error');
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

  let reEnrollSocket = null;

  function abrirModalReEnrolamiento(voter) {
    let modal = document.getElementById('re-enroll-modal');
    if (!modal) {
      modal = document.createElement('div');
      modal.id = 're-enroll-modal';
      modal.className = 'voter-modal-overlay';
      modal.innerHTML = `
        <div class="voter-modal-panel" style="max-width:480px">
          <div class="modal-header">
            <h2>Re-enrolamiento biometrico</h2>
            <button class="modal-close" id="btnCloseReEnroll">&times;</button>
          </div>
          <p style="color:var(--abis-text-2);font-size:0.84rem;margin-bottom:4px">
            <strong id="reEnrollName"></strong> (<span id="reEnrollId"></span>)
          </p>
          <div style="background:#f8f9fa;border-radius:8px;padding:20px;text-align:center;margin:14px 0">
            <div style="width:64px;height:64px;margin:0 auto 12px;border:3px solid #52b788;border-radius:50%;display:flex;align-items:center;justify-content:center">
              <span class="material-symbols-outlined" style="font-size:32px;color:#52b788">fingerprint</span>
            </div>
            <div id="reEnrollStatus" style="font-size:0.84rem;color:#333;font-weight:600">Conectando al lector biometrico...</div>
            <div id="reEnrollProgress" style="font-size:0.76rem;color:var(--abis-text-2);margin-top:4px">Muestras: 0/4</div>
            <div style="background:#e5e7eb;border-radius:999px;height:8px;margin-top:10px;overflow:hidden">
              <div id="reEnrollBar" style="background:#52b788;height:100%;width:0%;transition:width 0.3s ease"></div>
            </div>
          </div>
          <div class="modal-actions">
            <button class="voters-btn voters-btn-ghost" id="btnCancelReEnroll">Cancelar</button>
          </div>
        </div>
      `;
      document.body.appendChild(modal);
      modal.querySelector('#btnCloseReEnroll')?.addEventListener('click', cerrarReEnroll);
      modal.querySelector('#btnCancelReEnroll')?.addEventListener('click', cerrarReEnroll);
    }

    document.getElementById('reEnrollName').textContent = fullName(voter);
    document.getElementById('reEnrollId').textContent = voter.identificacion;
    document.getElementById('reEnrollStatus').textContent = 'Conectando al lector biometrico...';
    document.getElementById('reEnrollProgress').textContent = 'Muestras: 0/4';
    document.getElementById('reEnrollBar').style.width = '0%';
    modal.style.display = 'flex';

    conectarWebSocketReEnroll();
    iniciarEnrolamientoReEnroll(voter.identificacion);
  }

  function conectarWebSocketReEnroll() {
    if (reEnrollSocket && reEnrollSocket.readyState <= WebSocket.OPEN) return;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    reEnrollSocket = new WebSocket(`${protocol}//${window.location.host}/ws/biometria-ui`);
    reEnrollSocket.onmessage = (event) => {
      try {
        const progress = JSON.parse(event.data);
        const status = document.getElementById('reEnrollStatus');
        const progressText = document.getElementById('reEnrollProgress');
        const bar = document.getElementById('reEnrollBar');
        if (!status) return;
        if (progress.estado === 'FINALIZADO_EXITOSO') {
          status.textContent = progress.mensaje || 'Huella guardada exitosamente';
          if (bar) bar.style.width = '100%';
          if (progressText) progressText.textContent = 'Muestras: 4/4';
        } else if (progress.estado === 'ERROR') {
          status.textContent = 'Error: ' + (progress.mensaje || progress.error || 'Fallo el lector');
          status.style.color = '#991b1b';
        } else {
          status.textContent = progress.mensaje || 'Capturando huella...';
          if (bar) bar.style.width = (progress.progreso || 0) + '%';
          if (progressText) progressText.textContent = 'Muestras: ' + (progress.samples || 0) + '/4';
        }
      } catch (e) { /* ignore */ }
    };
    reEnrollSocket.onerror = () => {};
  }

  async function iniciarEnrolamientoReEnroll(identificacion) {
    try {
      const result = await fetch('/api/enroll', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...headers },
        body: JSON.stringify({ identificacion, re_enroll: true })
      });
      const data = await result.json().catch(() => ({}));
      const status = document.getElementById('reEnrollStatus');
      if (result.ok && (data.success || data.persisted)) {
        if (status) {
          status.textContent = 'Huella biometrica actualizada correctamente.';
          status.style.color = '#065f46';
        }
        setTimeout(async () => { cerrarReEnroll(); await loadVoters(); }, 2000);
      } else {
        if (status) {
          status.textContent = 'Error: ' + (data.detail || data.error || data.message || 'No se pudo completar el enrolamiento');
          status.style.color = '#991b1b';
        }
      }
    } catch (e) {
      const status = document.getElementById('reEnrollStatus');
      if (status) {
        status.textContent = 'Error de conexion: ' + (e.message || 'Sin respuesta del servicio biometrico');
        status.style.color = '#991b1b';
      }
    }
  }

  function cerrarReEnroll() {
    if (reEnrollSocket) { reEnrollSocket.close(); reEnrollSocket = null; }
    const modal = document.getElementById('re-enroll-modal');
    if (modal) modal.style.display = 'none';
  }

  window.cambiarFotoVotante = async function (event, identificacion) {
    const file = event.target.files?.[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('foto', file);
    formData.append('identificacion', identificacion);
    const label = document.getElementById('drawer-photo-upload-label');
    const photoDiv = document.getElementById('drawer-photo');
    try {
      if (label) label.style.opacity = '0.6';
      const response = await fetch('/api/votantes/foto', { method: 'POST', body: formData });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const data = await response.json().catch(() => ({}));
      const newUrl = (data?.data?.foto_url || data?.foto_url || data?.url) + '?t=' + Date.now();
      if (photoDiv) {
        photoDiv.innerHTML = `<img src="${escapeHtml(newUrl)}" alt="Foto actualizada" loading="lazy">`;
      }
    } catch (e) {
      showToast('No fue posible subir la foto: ' + (e.message || 'Error de conexion'), 'error');
    } finally {
      if (label) label.style.opacity = '1';
    }
  };

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
    tbody.innerHTML = Array.from({length:6},function(_,i){return '<tr><td colspan="9"><div class="skeleton-row skeleton-tall" style="width:'+(100-i*10)+'%"></div></td></tr>';}).join('');
    try {
      const response = await fetch('/api/votantes', { headers });
      if (!response.ok) throw new Error('No fue posible cargar votantes');
      const payload = await response.json();
      state.voters = Array.isArray(payload) ? payload : (payload.data || []);
      state.filtered = [...state.voters];
      await loadKpiStats();
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
  document.getElementById('voters-import-input').addEventListener('change', () => showToast('Importacion masiva pendiente de endpoint backend.', 'warning'));
  document.getElementById('voters-new').addEventListener('click', () => { window.location.href = '/pages/registro/index.html'; });
  document.getElementById('voters-audit-toggle').addEventListener('click', openAuditFullModal);
  document.getElementById('voters-audit-link').addEventListener('click', () => { closeAuditSlideover(); openAuditFullModal(); });
  document.getElementById('audit-close-btn').addEventListener('click', closeAuditSlideover);
  document.getElementById('audit-overlay').addEventListener('click', closeAuditSlideover);
  document.getElementById('voter-drawer-close').addEventListener('click', closeDrawer);
  document.getElementById('voter-drawer').addEventListener('click', (event) => {
    if (event.target.id === 'voter-drawer') closeDrawer();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      closePopover();
      closeDrawer();
      closeAuditSlideover();
      closeAuditFullModal();
    }
  });

  initColumnToggle();
  initAuditFullModal();
  loadPuestos().then(() => loadVoters());
})();

