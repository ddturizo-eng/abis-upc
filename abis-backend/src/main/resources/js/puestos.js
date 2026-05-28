var PuestosAdmin = window.PuestosAdmin || {
  editandoId: null,
  allPuestos: [],

  init: function() {
    this.cargar();
    document.getElementById('formPuesto').addEventListener('submit', function(e) { PuestosAdmin.guardar(e); });
    var self = this;
    document.getElementById('puestosSearch').addEventListener('input', function() { self.filtrar(); });
  },

  formatearFecha: function(iso) {
    if (!iso) return '—';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return d.toLocaleDateString('es-CO', { day:'2-digit', month:'2-digit', year:'numeric' }) + ' ' + d.toLocaleTimeString('es-CO', { hour:'2-digit', minute:'2-digit', hour12:true });
  },

  async cargar() {
    var tbody = document.getElementById('puestos-tbody');
    tbody.innerHTML = Array.from({ length: 4 }, function() { return '<tr><td colspan="6"><div class="skeleton-bar"></div></td></tr>'; }).join('');
    try {
      var res = await fetch('/api/puestos');
      if (!res.ok) throw new Error('Error');
      var data = await res.json();
      this.allPuestos = Array.isArray(data) ? data : (data.data || []);
      this.actualizarMetricas(this.allPuestos);
      this.renderizar(this.allPuestos);
    } catch (e) {
      tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:32px;color:#6b7280">Error al cargar puestos</td></tr>';
    }
  },

  actualizarMetricas: function(puestos) {
    var sedes = {}, ciudades = {};
    puestos.forEach(function(p) {
      if (p.sede) sedes[p.sede] = true;
      if (p.ciudad) ciudades[p.ciudad] = true;
    });
    document.getElementById('totalPuestos').textContent = puestos.length;
    document.getElementById('totalSedes').textContent = Object.keys(sedes).length;
    document.getElementById('totalCiudades').textContent = Object.keys(ciudades).length;
  },

  filtrar: function() {
    var q = (document.getElementById('puestosSearch').value || '').trim().toLowerCase();
    if (!q) { this.renderizar(this.allPuestos); return; }
    var self = this;
    this.renderizar(this.allPuestos.filter(function(p) {
      return (p.nombrePuesto || p.nombre_puesto || '').toLowerCase().indexOf(q) >= 0 ||
        (p.ciudad || '').toLowerCase().indexOf(q) >= 0 ||
        (p.sede || '').toLowerCase().indexOf(q) >= 0;
    }));
  },

  renderizar: function(puestos) {
    var tbody = document.getElementById('puestos-tbody');
    document.getElementById('puestosPageInfo').textContent = 'Mostrando ' + puestos.length + ' de ' + this.allPuestos.length + ' puestos';
    if (!puestos.length) {
      tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:32px;color:#9ca3af">Sin resultados</td></tr>';
      return;
    }
    var self = this;
    tbody.innerHTML = puestos.map(function(p) {
      var id = p.id || p.idPuesto || '';
      var nombre = p.nombrePuesto || p.nombre_puesto || '—';
      var ciudad = p.ciudad || '—';
      var sede = p.sede || '—';
      var inicio = self.formatearFecha(p.horaInicio || p.hora_inicio);
      var salida = self.formatearFecha(p.horaSalida || p.hora_salida);
      return '<tr>'
        + '<td style="color:#9ca3af;font-size:11px">#' + id + '</td>'
        + '<td><div class="puesto-name">' + self.esc(nombre) + '</div><div class="puesto-status"><span class="puesto-status-dot"></span>Activo</div></td>'
        + '<td>' + self.esc(ciudad) + '</td>'
        + '<td><span class="puesto-sede-link">' + self.esc(sede) + '</span></td>'
        + '<td><div class="puesto-horario"><span class="puesto-horario-in">' + inicio + '</span><span class="puesto-horario-out">' + salida + '</span></div></td>'
        + '<td><div class="puesto-actions">'
        + '<button class="puesto-action-btn" onclick="PuestosAdmin.editar(' + id + ')" title="Editar"><span class="material-symbols-outlined" style="font-size:18px">edit</span></button>'
        + '<button class="puesto-action-btn puesto-action-danger" onclick="PuestosAdmin.eliminar(' + id + ')" title="Eliminar"><span class="material-symbols-outlined" style="font-size:18px">delete</span></button>'
        + '</div></td></tr>';
    }).join('');
  },

  abrirModal: function(puesto) {
    document.getElementById('formPuesto').reset();
    document.getElementById('puestoId').value = '';
    if (puesto) {
      this.editandoId = puesto.id || puesto.idPuesto;
      document.getElementById('panelPuestoTitulo').textContent = 'Editar puesto de votacion';
      document.getElementById('puestoNombre').value = puesto.nombrePuesto || puesto.nombre_puesto || '';
      document.getElementById('puestoCiudad').value = puesto.ciudad || '';
      document.getElementById('puestoSede').value = puesto.sede || 'SEDE CENTRAL';
      document.getElementById('puestoInicio').value = this.fechaLocale(puesto.horaInicio || puesto.hora_inicio);
      document.getElementById('puestoSalida').value = this.fechaLocale(puesto.horaSalida || puesto.hora_salida);
    } else {
      this.editandoId = null;
      document.getElementById('panelPuestoTitulo').textContent = 'Nuevo puesto de votacion';
    }
    document.getElementById('panelPuesto').classList.add('open');
    document.getElementById('overlayPuesto').classList.add('open');
    document.body.style.overflow = 'hidden';
  },

  cerrarModal: function() {
    document.getElementById('panelPuesto').classList.remove('open');
    document.getElementById('overlayPuesto').classList.remove('open');
    this.editandoId = null;
    document.body.style.overflow = '';
  },

  editar: async function(id) {
    try {
      var res = await fetch('/api/puestos');
      var data = await res.json();
      var puestos = Array.isArray(data) ? data : (data.data || []);
      var puesto = puestos.find(function(p) { return (p.id || p.idPuesto) == id; });
      if (puesto) this.abrirModal(puesto);
    } catch (e) {}
  },

  guardar: async function(event) {
    event.preventDefault();
    var payload = {
      ciudad: document.getElementById('puestoCiudad').value.trim(),
      sede: document.getElementById('puestoSede').value,
      nombrePuesto: document.getElementById('puestoNombre').value.trim(),
      horaInicio: document.getElementById('puestoInicio').value,
      horaSalida: document.getElementById('puestoSalida').value
    };
    if (!payload.ciudad || !payload.nombrePuesto || !payload.horaInicio || !payload.horaSalida) {
      if (window.showToast) window.showToast('Complete todos los campos', 'error'); return;
    }
    if (payload.horaInicio >= payload.horaSalida) {
      if (window.showToast) window.showToast('La salida debe ser posterior al inicio', 'error'); return;
    }
    var btn = document.getElementById('btnGuardarPuesto');
    btn.disabled = true; btn.innerHTML = '<span class="material-symbols-outlined">hourglass_top</span> Guardando...';
    try {
      var url = this.editandoId ? '/api/puestos/' + this.editandoId : '/api/puestos';
      var res = await fetch(url, {
        method: this.editandoId ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (localStorage.getItem('abis_token') || '') },
        body: JSON.stringify(payload)
      });
      if (!res.ok) { var err = await res.json().catch(function() { return {}; }); throw new Error(err.error || 'HTTP ' + res.status); }
      if (window.showToast) window.showToast(this.editandoId ? 'Puesto actualizado' : 'Puesto creado', 'success');
      this.cerrarModal(); this.cargar();
    } catch (e) { if (window.showToast) window.showToast(e.message, 'error'); }
    finally { btn.disabled = false; btn.innerHTML = '<span class="material-symbols-outlined">save</span> Guardar puesto'; }
  },

  eliminar: async function(id) {
    if (!confirm('Eliminar este puesto de votacion?')) return;
    try {
      var res = await fetch('/api/puestos/' + id, { method: 'DELETE', headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('abis_token') || '') } });
      if (!res.ok) { var err = await res.json().catch(function() { return {}; }); throw new Error(err.error || 'HTTP ' + res.status); }
      if (window.showToast) window.showToast('Puesto eliminado', 'success');
      this.cargar();
    } catch (e) { if (window.showToast) window.showToast(e.message, 'error'); }
  },

  fechaLocale: function(iso) {
    if (!iso) return '';
    try {
      var d = new Date(iso); if (isNaN(d.getTime())) return iso;
      var pad = function(n) { return String(n).padStart(2, '0'); };
      return d.getFullYear() + '-' + pad(d.getMonth()+1) + '-' + pad(d.getDate()) + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    } catch (e) { return iso; }
  },

  esc: function(v) { return String(v||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
};

PuestosAdmin.init();
