(function () {
  const state = {
    certificados: [],
    filtrados: [],
    selectedKey: null,
    enviando: false
  };

  const els = {};
  const fmt = new Intl.DateTimeFormat('es-CO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });

  function init() {
    Object.assign(els, {
      eleccion: document.getElementById('cert-eleccion'),
      refresh: document.getElementById('cert-refresh'),
      search: document.getElementById('cert-search'),
      resendSelected: document.getElementById('cert-resend-selected'),
      table: document.getElementById('cert-table'),
      total: document.getElementById('cert-total'),
      enviados: document.getElementById('cert-enviados'),
      pendientes: document.getElementById('cert-pendientes'),
      errores: document.getElementById('cert-errores')
    });

    els.refresh?.addEventListener('click', cargar);
    els.eleccion?.addEventListener('change', cargar);
    els.search?.addEventListener('input', aplicarFiltro);
    els.resendSelected?.addEventListener('click', () => {
      const item = state.filtrados.find((cert) => itemKey(cert) === state.selectedKey);
      if (item) reenviar(item);
    });

    cargarElecciones();
    cargar();
  }

  async function cargarElecciones() {
    try {
      const payload = await ApiCertificados.elecciones();
      const elecciones = normalizarLista(payload);
      els.eleccion.innerHTML = '<option value="">Todas las elecciones</option>' + elecciones.map((eleccion) => `
        <option value="${escapeHtml(eleccion.id)}">${escapeHtml(eleccion.nombre)} (${escapeHtml(eleccion.estado || '--')})</option>
      `).join('');
    } catch (error) {
      notificar(`No fue posible cargar elecciones: ${error.message}`, 'error');
    }
  }

  async function cargar() {
    try {
      setLoading(true);
      const eleccionId = els.eleccion?.value || '';
      const [listaPayload, resumenPayload] = await Promise.all([
        ApiCertificados.listar(eleccionId, 100),
        ApiCertificados.resumen(eleccionId)
      ]);
      state.certificados = normalizarLista(listaPayload);
      actualizarResumen(resumenPayload?.data || {});
      aplicarFiltro();
    } catch (error) {
      els.table.innerHTML = `<tr><td colspan="7" class="px-4 py-10 text-center text-red-500">${escapeHtml(error.message)}</td></tr>`;
    } finally {
      setLoading(false);
    }
  }

  function aplicarFiltro() {
    const query = normalizar(els.search?.value || '');
    state.filtrados = state.certificados.filter((cert) => {
      if (!query) return true;
      return [
        cert.identificacion,
        cert.nombre,
        cert.correo,
        cert.eleccion,
        cert.estado,
        cert.codigoCertificado
      ].some((value) => normalizar(value).includes(query));
    });
    renderTabla();
  }

  function renderTabla() {
    actualizarBotonSeleccion();
    if (!state.filtrados.length) {
      els.table.innerHTML = '<tr><td colspan="7" class="px-4 py-10 text-center text-slate-400">No hay certificados para mostrar.</td></tr>';
      return;
    }

    els.table.innerHTML = state.filtrados.map((cert) => {
      const key = itemKey(cert);
      const checked = state.selectedKey === key ? 'checked' : '';
      const canResend = cert.identificacion && cert.idEleccion;
      return `
        <tr class="hover:bg-[#f8faf9]">
          <td class="px-4 py-4 align-top">
            <input type="radio" name="cert-selected" value="${escapeHtml(key)}" ${checked}>
          </td>
          <td class="px-4 py-4 align-top">
            <div class="font-semibold text-[#0a2e1f]">${escapeHtml(cert.nombre || 'Votante registrado')}</div>
            <div class="text-xs text-slate-500">${escapeHtml(cert.identificacion || '--')}</div>
            <div class="text-xs text-slate-500">${escapeHtml(cert.correo || '--')}</div>
          </td>
          <td class="px-4 py-4 align-top">
            <div class="font-medium text-slate-700">${escapeHtml(cert.eleccion || `Eleccion ${cert.idEleccion || '--'}`)}</div>
            <div class="text-xs text-slate-400">ID ${escapeHtml(cert.idEleccion || '--')}</div>
          </td>
          <td class="px-4 py-4 align-top">${estadoBadge(cert.estado)}</td>
          <td class="px-4 py-4 align-top font-mono text-xs text-slate-600">${escapeHtml(cert.codigoCertificado || 'Sin codigo')}</td>
          <td class="px-4 py-4 align-top">
            <div class="text-slate-700">${escapeHtml(fecha(cert.fechaEnvio || cert.fechaSolicitud))}</div>
            <div class="max-w-[260px] truncate text-xs text-slate-400" title="${escapeHtml(cert.observaciones || '')}">${escapeHtml(cert.observaciones || cert.messageId || '')}</div>
          </td>
          <td class="px-4 py-4 align-top text-right">
            <button class="cert-resend inline-flex items-center justify-center gap-2 rounded-lg border border-outline px-3 py-2 text-xs font-semibold text-[#0a2e1f] disabled:opacity-50" data-key="${escapeHtml(key)}" ${canResend ? '' : 'disabled'}>
              <span class="material-symbols-outlined text-[16px]">outgoing_mail</span>
              Reenviar
            </button>
          </td>
        </tr>
      `;
    }).join('');

    els.table.querySelectorAll('input[name="cert-selected"]').forEach((input) => {
      input.addEventListener('change', () => {
        state.selectedKey = input.value;
        actualizarBotonSeleccion();
      });
    });
    els.table.querySelectorAll('.cert-resend').forEach((button) => {
      button.addEventListener('click', () => {
        const item = state.filtrados.find((cert) => itemKey(cert) === button.dataset.key);
        if (item) reenviar(item);
      });
    });
  }

  async function reenviar(cert) {
    if (state.enviando) return;
    try {
      state.enviando = true;
      setLoading(true);
      if (cert.idAuditoria) {
        await ApiCertificados.reenviar(cert.idAuditoria);
      } else {
        await ApiCertificados.reenviarVotante(cert.identificacion, cert.idEleccion);
      }
      notificar('Certificado reenviado correctamente', 'success');
      await cargar();
    } catch (error) {
      notificar(`No fue posible reenviar: ${error.message}`, 'error');
      await cargar();
    } finally {
      state.enviando = false;
      setLoading(false);
    }
  }

  function actualizarResumen(resumen) {
    els.total.textContent = resumen.total ?? '--';
    els.enviados.textContent = resumen.enviados ?? '--';
    els.pendientes.textContent = resumen.pendientes ?? '--';
    els.errores.textContent = resumen.errores ?? '--';
  }

  function actualizarBotonSeleccion() {
    if (!els.resendSelected) return;
    const selected = state.filtrados.find((cert) => itemKey(cert) === state.selectedKey);
    els.resendSelected.disabled = !selected || state.enviando;
  }

  function setLoading(value) {
    [els.refresh, els.resendSelected].forEach((el) => {
      if (el) el.disabled = value || (el === els.resendSelected && !state.selectedKey);
    });
  }

  function estadoBadge(estado) {
    const value = String(estado || 'SIN_SOLICITUD').toUpperCase();
    const map = {
      ENVIADO: 'bg-emerald-50 text-emerald-700 border-emerald-100',
      ERROR: 'bg-red-50 text-red-700 border-red-100',
      SOLICITADO: 'bg-amber-50 text-amber-700 border-amber-100',
      PENDIENTE_REINTENTO: 'bg-amber-50 text-amber-700 border-amber-100',
      SIN_SOLICITUD: 'bg-slate-50 text-slate-600 border-slate-100'
    };
    const label = value.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (char) => char.toUpperCase());
    return `<span class="inline-flex rounded-full border px-2.5 py-1 text-xs font-semibold ${map[value] || map.SIN_SOLICITUD}">${escapeHtml(label)}</span>`;
  }

  function fecha(value) {
    if (!value) return '--';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '--';
    return fmt.format(date);
  }

  function itemKey(cert) {
    return cert.idAuditoria ? `a-${cert.idAuditoria}` : `v-${cert.identificacion}-${cert.idEleccion}`;
  }

  function normalizarLista(payload) {
    if (Array.isArray(payload)) return payload;
    if (Array.isArray(payload?.data)) return payload.data;
    return [];
  }

  function normalizar(value) {
    return String(value || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  }

  function notificar(mensaje, tipo = 'info') {
    if (typeof mostrarNotificacion === 'function') {
      mostrarNotificacion(mensaje, tipo);
      return;
    }
    console[tipo === 'error' ? 'error' : 'log'](mensaje);
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (char) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#039;'
    }[char]));
  }

  init();
})();
