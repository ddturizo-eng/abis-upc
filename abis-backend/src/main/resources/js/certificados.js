(function () {
  const state = {
    certificados: [],
    filtrados: [],
    selectedKey: null,
    enviando: false,
    actividad: []
  };

  const els = {};
  const fmt = new Intl.DateTimeFormat('es-CO', { day:'2-digit', month:'2-digit', year:'numeric', hour:'2-digit', minute:'2-digit' });
  const fmtShort = new Intl.RelativeTimeFormat('es', { style:'narrow' });

  function init() {
    Object.assign(els, {
      tabs: document.querySelectorAll('#cert-tabs .cert-tab'),
      tabContents: document.querySelectorAll('.cert-tab-content'),
      eleccion: document.getElementById('cert-eleccion'),
      refresh: document.getElementById('cert-refresh'),
      search: document.getElementById('cert-search'),
      table: document.getElementById('cert-table'),
      total: document.getElementById('cert-total'),
      enviados: document.getElementById('cert-enviados'),
      pendientes: document.getElementById('cert-pendientes'),
      errores: document.getElementById('cert-errores'),
      pageInfo: document.getElementById('cert-page-info'),
      activity: document.getElementById('cert-activity'),
      donutChart: document.getElementById('cert-donut-chart'),
      donutLegend: document.getElementById('cert-donut-legend'),
      enviarPendientes: document.getElementById('cert-enviar-pendientes'),
      actaEleccion: document.getElementById('acta-eleccion'),
      actaGenerar: document.getElementById('acta-generar'),
      actaResultado: document.getElementById('acta-resultado'),
      cartasEleccion: document.getElementById('cartas-eleccion'),
      cartasGenerar: document.getElementById('cartas-generar'),
      cartasNotificar: document.getElementById('cartas-notificar'),
      cartasTabla: document.getElementById('cartas-tabla'),
      cartasTablaWrap: document.getElementById('cartas-tabla-wrap'),
      verifCodigo: document.getElementById('verif-codigo'),
      verifBuscar: document.getElementById('verif-buscar'),
      verifResultado: document.getElementById('verif-resultado')
    });

    // Tabs
    els.tabs.forEach(tab => tab.addEventListener('click', () => switchTab(tab.dataset.tab)));
    els.refresh?.addEventListener('click', cargarPostVoto);
    els.eleccion?.addEventListener('change', cargarPostVoto);
    els.search?.addEventListener('input', aplicarFiltro);
    els.enviarPendientes?.addEventListener('click', enviarPendientes);
    els.actaEleccion?.addEventListener('change', () => { if (els.actaGenerar) els.actaGenerar.disabled = !els.actaEleccion.value; });
    els.actaGenerar?.addEventListener('click', generarActa);
    els.cartasEleccion?.addEventListener('change', onCartasEleccionChange);
    els.cartasGenerar?.addEventListener('click', cargarJuradosDesignados);
    els.cartasNotificar?.addEventListener('click', notificarTodosJurados);
    els.verifBuscar?.addEventListener('click', verificarCertificado);

    document.getElementById('cert-descargar-todos')?.addEventListener('click', () => notificar('Funcionalidad en desarrollo', 'info'));
    document.getElementById('cert-reporte')?.addEventListener('click', () => notificar('Funcionalidad en desarrollo', 'info'));

    cargarElecciones();
    cargarPostVoto();
    cargarActividadReciente();
  }

  function switchTab(tabId) {
    els.tabs.forEach(t => t.classList.toggle('active', t.dataset.tab === tabId));
    els.tabContents.forEach(c => c.classList.toggle('active', c.id === 'tab-' + tabId));
    if (tabId === 'postvoto') cargarPostVoto();
    if (tabId === 'actas') cargarEleccionesActa();
    if (tabId === 'cartas') cargarEleccionesCartas();
  }

  /* ─── POST-VOTO ─── */
  async function cargarElecciones() {
    try {
      const payload = await ApiCertificados.elecciones();
      const elecciones = normalizarLista(payload);
      els.eleccion.innerHTML = '<option value="">Todas las elecciones</option>' +
        elecciones.map(e => `<option value="${escapeHtml(e.id)}">${escapeHtml(e.nombre)} (${escapeHtml(e.estado || '--')})</option>`).join('');
    } catch (error) {
      notificar(`No fue posible cargar elecciones: ${error.message}`, 'error');
    }
  }

  async function cargarPostVoto() {
    try {
      setLoading(true);
      const eleccionId = els.eleccion?.value || '';
      const [listaPayload, resumenPayload] = await Promise.all([
        ApiCertificados.listar(eleccionId, 200),
        ApiCertificados.resumen(eleccionId)
      ]);
      state.certificados = normalizarLista(listaPayload);
      actualizarResumen(resumenPayload?.data || {});
      actualizarDonut(eleccionId);
      aplicarFiltro();
    } catch (error) {
      els.table.innerHTML = `<tr><td colspan="6" class="cert-empty">${escapeHtml(error.message)}</td></tr>`;
    } finally {
      setLoading(false);
    }
  }

  function aplicarFiltro() {
    const query = normalizar(els.search?.value || '');
    state.filtrados = state.certificados.filter(cert => {
      if (!query) return true;
      return [cert.identificacion, cert.nombre, cert.correo, cert.eleccion, cert.estado, cert.codigoCertificado]
        .some(v => normalizar(v).includes(query));
    });
    renderTabla();
  }

  function renderTabla() {
    if (!state.filtrados.length) {
      els.table.innerHTML = '<tr><td colspan="6" class="cert-empty">No hay certificados para mostrar.</td></tr>';
      els.pageInfo.textContent = '0 registros';
      return;
    }
    els.pageInfo.textContent = `${state.filtrados.length} de ${state.certificados.length} registros`;

    els.table.innerHTML = state.filtrados.map(cert => {
      return `
        <tr>
          <td>
            <div style="font-weight:600;color:#1a3a2a">${escapeHtml(cert.nombre || 'Votante')}</div>
            <div style="font-size:0.72rem;color:#6b7280">${escapeHtml(cert.identificacion || '--')}</div>
            <div style="font-size:0.7rem;color:#9ca3af">${escapeHtml(cert.correo || '--')}</div>
          </td>
          <td><div style="font-weight:500">${escapeHtml(cert.eleccion || `Eleccion ${cert.idEleccion || '--'}`)}</div></td>
          <td>${estadoBadge(cert.estado)}</td>
          <td style="font-family:monospace;font-size:0.7rem">${escapeHtml(cert.codigoCertificado || '--')}</td>
          <td style="font-size:0.78rem">${fecha(cert.fechaEnvio || cert.fechaSolicitud)}</td>
          <td>
            <div style="display:flex;gap:4px">
              <button class="cert-btn cert-btn-ghost" style="padding:0 10px;min-height:32px;font-size:0.72rem" onclick="window._certReenviar ? window._certReenviar('${escapeHtml(itemKey(cert))}') : null" title="Reenviar">
                <span class="material-symbols-outlined" style="font-size:16px">outgoing_mail</span>
              </button>
            </div>
          </td>
        </tr>
      `;
    }).join('');

    els.table.querySelectorAll('button[title="Reenviar"]').forEach(btn => {
      btn.addEventListener('click', async () => {
        const cert = state.filtrados.find(c => itemKey(c) === btn.dataset?.key);
        if (cert) await reenviar(cert);
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
      agregarActividad('Certificado reenviado', cert.nombre || cert.identificacion, 'success');
      await cargarPostVoto();
    } catch (error) {
      notificar(`No fue posible reenviar: ${error.message}`, 'error');
      agregarActividad('Error de envio', cert.nombre || cert.identificacion, 'error');
      await cargarPostVoto();
    } finally {
      state.enviando = false;
      setLoading(false);
    }
  }

  async function enviarPendientes() {
    const pendientes = state.certificados.filter(c => c.estado !== 'ENVIADO');
    if (!pendientes.length) { notificar('No hay certificados pendientes', 'info'); return; }
    notificar(`Enviando ${pendientes.length} certificados pendientes...`, 'info');
    for (const cert of pendientes) {
      await reenviar(cert);
      await new Promise(r => setTimeout(r, 500));
    }
  }

  function actualizarResumen(resumen) {
    els.total.textContent = resumen.total ?? '--';
    els.enviados.textContent = resumen.enviados ?? '--';
    els.pendientes.textContent = resumen.pendientes ?? '--';
    els.errores.textContent = resumen.errores ?? '--';
    if (els.enviarPendientes) els.enviarPendientes.disabled = !(resumen.pendientes > 0 || resumen.errores > 0);
  }

  /* ─── DONUT ─── */
  function actualizarDonut(eleccionId) {
    if (!els.donutChart || !els.donutLegend) return;
    const byEleccion = {};
    state.certificados.forEach(c => {
      const key = c.eleccion || `ID ${c.idEleccion}`;
      byEleccion[key] = (byEleccion[key] || 0) + 1;
    });
    const total = state.certificados.length;
    if (total === 0) {
      els.donutChart.innerHTML = '<div style="color:#9ca3af;font-size:0.78rem;text-align:center">Sin datos</div>';
      els.donutLegend.innerHTML = '';
      return;
    }
    const colores = ['#158759','#2d6a4f','#52b788','#a5e9c9','#1a3a2a','#40916c'];
    const slices = Object.entries(byEleccion).sort((a,b) => b[1] - a[1]).slice(0,6);
    const svgSize = 120;
    const r = 42;
    const cx = 60, cy = 60;
    const circumference = 2 * Math.PI * r;
    let offset = 0;
    let paths = '';
    slices.forEach(([,count], i) => {
      const pct = count / total;
      const dash = pct * circumference;
      paths += `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${colores[i]}" stroke-width="18"
        stroke-dasharray="${dash} ${circumference - dash}"
        stroke-dashoffset="${-offset}"
        transform="rotate(-90 ${cx} ${cy})" />`;
      offset += dash;
    });
    els.donutChart.innerHTML = `<svg viewBox="0 0 120 120" style="display:block;margin:0 auto"><circle cx="60" cy="60" r="42" fill="none" stroke="#f3f4f6" stroke-width="18" />${paths}</svg>`;
    els.donutLegend.innerHTML = slices.map(([label, count], i) => `
      <div class="cert-donut-legend-item">
        <span class="cert-donut-dot" style="background:${colores[i]}"></span>
        <span class="cert-donut-label">${escapeHtml(label)}</span>
        <span class="cert-donut-count">${Math.round(count/total*100)}%</span>
      </div>
    `).join('');
  }

  /* ─── ACTIVIDAD ─── */
  function cargarActividadReciente() {
    const items = [
      { texto:'Modulo de certificados cargado', nombre:'Sistema', tipo:'success', ts:Date.now() }
    ];
    state.actividad = items;
    renderActividad();
  }

  function agregarActividad(texto, nombre, tipo) {
    state.actividad.unshift({ texto, nombre: nombre || 'Sistema', tipo, ts: Date.now() });
    if (state.actividad.length > 20) state.actividad.length = 20;
    renderActividad();
  }

  function renderActividad() {
    if (!els.activity) return;
    if (!state.actividad.length) {
      els.activity.innerHTML = '<p class="cert-activity-empty">Sin actividad reciente</p>';
      return;
    }
    els.activity.innerHTML = state.actividad.slice(0, 10).map(a => {
      const dotClass = a.tipo === 'error' ? 'error' : a.tipo === 'warn' ? 'warn' : '';
      const mins = Math.round((Date.now() - a.ts) / 60000);
      const time = mins < 1 ? 'Ahora' : mins < 60 ? `Hace ${mins} min` : `Hace ${Math.round(mins/60)}h`;
      return `
        <div class="cert-activity-item">
          <span class="cert-activity-dot ${dotClass}"></span>
          <div>
            <div class="cert-activity-text">${escapeHtml(a.texto)} <span style="font-weight:600">${escapeHtml(a.nombre)}</span></div>
            <div class="cert-activity-time">${time}</div>
          </div>
        </div>
      `;
    }).join('');
  }

  /* ─── ACTAS DE GANADORES ─── */
  async function cargarEleccionesActa() {
    try {
      const payload = await ApiCertificados.elecciones();
      const elecciones = normalizarLista(payload).filter(e => e.estado === 'CERRADA' || e.estado === 'FINALIZADA');
      els.actaEleccion.innerHTML = '<option value="">Seleccionar eleccion finalizada...</option>' +
        elecciones.map(e => `<option value="${escapeHtml(e.id)}">${escapeHtml(e.nombre)}</option>`).join('');
    } catch (e) { /* ignore */ }
  }

  async function generarActa() {
    const id = els.actaEleccion?.value;
    if (!id) return;
    try {
      const response = await fetch(`/api/elecciones/${id}/resultados`, { headers: headers() });
      if (!response.ok) throw new Error('Eleccion sin resultados');
      const data = await response.json();
      const res = data?.data || data;
      const candidatos = res?.candidatos || [];
      if (!candidatos.length) { notificar('No hay candidatos en esta eleccion', 'warning'); return; }

      let html = '<h4 style="margin-bottom:8px">Resultados</h4><table style="width:100%;font-size:0.82rem"><thead><tr><th>Candidato</th><th>Cargo</th><th>Votos</th></tr></thead><tbody>';
      candidatos.sort((a,b) => (b.votos||0) - (a.votos||0)).forEach((c, i) => {
        html += `<tr style="${i===0?'background:#f0fdf4;font-weight:700':''}"><td>${i===0 ? '★ ' : ''}${escapeHtml(c.candidato || c.nombre || '')}</td><td>${escapeHtml(c.cargo || '')}</td><td>${c.votos || 0}</td></tr>`;
      });
      html += '</tbody></table>';
      html += '<button class="cert-btn cert-btn-primary" style="margin-top:12px" onclick="window.open(\'/api/elecciones/' + id + '/acta/pdf\',\'_blank\')"><span class="material-symbols-outlined">download</span> Descargar acta PDF</button>';

      els.actaResultado.innerHTML = html;
      els.actaResultado.style.display = 'block';
      notificar('Resultados cargados', 'success');
    } catch (e) {
      notificar('Error al cargar resultados: ' + e.message, 'error');
    }
  }

  /* ─── CARTAS DE DESIGNACION ─── */
  async function cargarEleccionesCartas() {
    try {
      const payload = await ApiCertificados.elecciones();
      const elecciones = normalizarLista(payload);
      els.cartasEleccion.innerHTML = '<option value="">Seleccionar eleccion...</option>' +
        elecciones.map(e => `<option value="${escapeHtml(e.id)}">${escapeHtml(e.nombre)} (${escapeHtml(e.estado || '--')})</option>`).join('');
    } catch (e) { /* ignore */ }
  }

  function onCartasEleccionChange() {
    const val = els.cartasEleccion?.value;
    els.cartasGenerar.disabled = !val;
    els.cartasNotificar.disabled = !val;
    els.cartasTablaWrap.style.display = 'none';
  }

  async function cargarJuradosDesignados() {
    const id = els.cartasEleccion?.value;
    if (!id) return;
    try {
      const payload = await fetch(`/api/jurados?eleccionId=${id}`, { headers: headers() });
      if (!payload.ok) throw new Error('Sin datos');
      const jurados = normalizarLista(await payload.json());
      if (!jurados.length) { els.cartasTabla.innerHTML = '<tr><td colspan="7" class="cert-empty">No hay jurados designados en esta eleccion</td></tr>'; }
      else {
        els.cartasTabla.innerHTML = jurados.map(j => `
          <tr>
            <td><strong>${escapeHtml(j.nombreCompleto || j.nombre || '--')}</strong><br><small style="color:#6b7280">${escapeHtml(j.identificacion || '')}</small></td>
            <td>${escapeHtml(j.mesa || j.idMesa || '--')}</td>
            <td>${escapeHtml(j.cargo || '--')}</td>
            <td>${escapeHtml(j.sede || j.nombrePuesto || '--')}</td>
            <td style="font-size:0.72rem">${escapeHtml(j.correo || '--')}</td>
            <td>${j.notificado ? '<span class="cert-badge" style="background:#dcfce7;color:#166534;border-color:#bbf7d0">Enviado</span>' : '<span class="cert-badge" style="background:#fef3c7;color:#92400e;border-color:#fde68a">Pendiente</span>'}</td>
            <td><button class="cert-btn cert-btn-ghost" style="padding:0 10px;min-height:30px;font-size:0.7rem" onclick="alert('Notificacion individual pendiente de implementacion')"><span class="material-symbols-outlined" style="font-size:16px">mail</span></button></td>
          </tr>
        `).join('');
      }
      els.cartasTablaWrap.style.display = 'block';
    } catch (e) {
      notificar('Error: ' + e.message, 'error');
    }
  }

  function notificarTodosJurados() {
    notificar('Notificacion masiva de jurados pendiente de implementacion', 'warning');
  }

  /* ─── VERIFICACION ─── */
  async function verificarCertificado() {
    const codigo = els.verifCodigo?.value?.trim();
    if (!codigo) { notificar('Ingrese un codigo de certificado', 'warning'); return; }
    try {
      const response = await fetch(`/api/certificados/verificar?codigo=${encodeURIComponent(codigo)}`, { headers: headers() });
      const data = await response.json().catch(() => ({}));
      if (response.ok && data?.valido) {
        els.verifResultado.style.display = 'block';
        els.verifResultado.innerHTML = `<div class="cert-actas-card"><span class="cert-actas-icon" style="background:#dcfce7;color:#166534"><span class="material-symbols-outlined">verified</span></span><div><h3>Certificado valido</h3><p>${escapeHtml(data.nombre || '')} - ${escapeHtml(data.eleccion || '')} - ${escapeHtml(data.fecha || '')}</p></div></div>`;
      } else {
        els.verifResultado.style.display = 'block';
        els.verifResultado.innerHTML = `<div class="cert-actas-card"><span class="cert-actas-icon" style="background:#fee2e2;color:#991b1b"><span class="material-symbols-outlined">cancel</span></span><div><h3>Certificado no encontrado</h3><p>El codigo ingresado no corresponde a un certificado valido.</p></div></div>`;
      }
    } catch (e) {
      notificar('Error al verificar: ' + e.message, 'error');
    }
  }

  /* ─── HELPERS ─── */
  function estadoBadge(estado) {
    const value = String(estado || 'SIN_SOLICITUD').toUpperCase();
    const map = {
      ENVIADO: 'background:#dcfce7;color:#166534;border-color:#bbf7d0',
      ERROR: 'background:#fee2e2;color:#991b1b;border-color:#fecaca',
      SOLICITADO: 'background:#fef3c7;color:#92400e;border-color:#fde68a',
      PENDIENTE_REINTENTO: 'background:#fef3c7;color:#92400e;border-color:#fde68a',
      SIN_SOLICITUD: 'background:#f3f4f6;color:#6b7280;border-color:#e5e7eb'
    };
    const label = value.replace(/_/g,' ').toLowerCase().replace(/^\w/, c => c.toUpperCase());
    return `<span class="cert-badge" style="${map[value] || map.SIN_SOLICITUD}">${escapeHtml(label)}</span>`;
  }

  function fecha(value) {
    if (!value) return '--';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '--';
    return fmt.format(date);
  }

  function itemKey(cert) { return cert.idAuditoria ? `a-${cert.idAuditoria}` : `v-${cert.identificacion}-${cert.idEleccion}`; }
  function normalizarLista(payload) { if (Array.isArray(payload)) return payload; if (Array.isArray(payload?.data)) return payload.data; return []; }
  function normalizar(value) { return String(value||'').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,''); }
  function setLoading(v) { if (els.refresh) els.refresh.disabled = v; }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]));
  }

  function notificar(mensaje, tipo) {
    window.showToast(mensaje, tipo);
  }

  function headers() {
    const token = localStorage.getItem('token');
    return token ? { Authorization: 'Bearer ' + token } : {};
  }

  init();
})();
