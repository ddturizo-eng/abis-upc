var AdministradoresAdmin = window.AdministradoresAdmin || {
  editandoId: null,
  allAdmins: [],

  init: function() {
    this.cargar();
    document.getElementById('formAdmin').addEventListener('submit', function(e) { AdministradoresAdmin.guardar(e); });
    var self = this;
    document.getElementById('adminsSearch').addEventListener('input', function() { self.filtrar(); });
  },

  async cargar() {
    var tbody = document.getElementById('admins-tbody');
    tbody.innerHTML = Array.from({ length: 3 }, function() { return '<tr><td colspan="6"><div class="skeleton-bar"></div></td></tr>'; }).join('');
    try {
      var res = await fetch('/api/admin/usuarios', { headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('abis_token') || '') } });
      if (!res.ok) throw new Error('Error');
      var result = await res.json();
      this.allAdmins = result.data || result || [];
      if (!Array.isArray(this.allAdmins)) this.allAdmins = [];
      document.getElementById('totalAdmins').textContent = this.allAdmins.length;
      this.renderizar(this.allAdmins);
    } catch (e) {
      tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:32px;color:#6b7280">Error al cargar administradores</td></tr>';
    }
  },

  filtrar: function() {
    var q = (document.getElementById('adminsSearch').value || '').trim().toLowerCase();
    if (!q) { this.renderizar(this.allAdmins); return; }
    var self = this;
    this.renderizar(this.allAdmins.filter(function(a) {
      return (a.usuario || '').toLowerCase().indexOf(q) >= 0 ||
        (a.nombre || '').toLowerCase().indexOf(q) >= 0 ||
        (a.correo || '').toLowerCase().indexOf(q) >= 0;
    }));
  },

  renderizar: function(admins) {
    var tbody = document.getElementById('admins-tbody');
    document.getElementById('adminsPageInfo').textContent = 'Mostrando ' + admins.length + ' de ' + this.allAdmins.length + ' administradores';
    if (!admins.length) {
      tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:32px;color:#9ca3af">Sin resultados</td></tr>';
      return;
    }
    var self = this;
    tbody.innerHTML = admins.map(function(a) {
      return '<tr>'
        + '<td style="color:#9ca3af;font-size:11px">#' + (a.id || '') + '</td>'
        + '<td><div class="admin-name">' + self.esc(a.usuario) + '</div></td>'
        + '<td>' + self.esc(a.nombre) + '</td>'
        + '<td>' + (a.correo ? '<span class="admin-email">' + self.esc(a.correo) + '</span>' : '<span style="color:#9ca3af">—</span>') + '</td>'
        + '<td><div class="admin-status"><span class="admin-status-dot"></span>Activo</div></td>'
        + '<td><div class="admin-actions">'
        + '<button class="admin-action-btn" onclick="AdministradoresAdmin.editar(' + a.id + ')" title="Editar"><span class="material-symbols-outlined" style="font-size:18px">edit</span></button>'
        + '<button class="admin-action-btn admin-action-danger" onclick="AdministradoresAdmin.eliminar(' + a.id + ',\'' + self.escAttr(a.usuario) + '\')" title="Eliminar"><span class="material-symbols-outlined" style="font-size:18px">delete</span></button>'
        + '</div></td></tr>';
    }).join('');
  },

  abrirModal: function(admin) {
    document.getElementById('formAdmin').reset();
    document.getElementById('adminId').value = '';
    document.getElementById('adminPasswordLabel').innerHTML = 'Contrasena <span class="admins-required">*</span>';

    if (admin) {
      this.editandoId = admin.id;
      document.getElementById('panelAdminTitulo').textContent = 'Editar administrador';
      document.getElementById('adminUsuario').value = admin.usuario || '';
      document.getElementById('adminUsuario').disabled = true;
      document.getElementById('adminNombre').value = admin.nombre || '';
      document.getElementById('adminCorreo').value = admin.correo || '';
      document.getElementById('adminPasswordLabel').innerHTML = 'Nueva contrasena <span style="color:#6b7280;font-weight:400">(dejar vacio = sin cambios)</span>';
    } else {
      this.editandoId = null;
      document.getElementById('panelAdminTitulo').textContent = 'Nuevo administrador';
      document.getElementById('adminUsuario').disabled = false;
    }

    document.getElementById('panelAdmin').classList.add('open');
    document.getElementById('overlayAdmin').classList.add('open');
    document.body.style.overflow = 'hidden';
  },

  cerrarModal: function() {
    document.getElementById('panelAdmin').classList.remove('open');
    document.getElementById('overlayAdmin').classList.remove('open');
    this.editandoId = null;
    document.body.style.overflow = '';
  },

  editar: async function(id) {
    try {
      var res = await fetch('/api/admin/usuarios', { headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('abis_token') || '') } });
      var result = await res.json();
      var data = Array.isArray(result.data) ? result.data : (Array.isArray(result) ? result : []);
      var admin = data.find(function(a) { return a.id == id; });
      if (admin) this.abrirModal(admin);
    } catch (e) {}
  },

  guardar: async function(event) {
    event.preventDefault();
    var usuario = document.getElementById('adminUsuario').value.trim();
    var nombre = document.getElementById('adminNombre').value.trim();
    var correo = document.getElementById('adminCorreo').value.trim();
    var password = document.getElementById('adminPassword').value;

    if (!usuario) { window.showToast('Usuario requerido', 'error'); return; }
    if (!nombre) { window.showToast('Nombre requerido', 'error'); return; }
    if (!this.editandoId && !password) { window.showToast('Contrasena requerida', 'error'); return; }
    if (correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo)) { window.showToast('Correo invalido', 'error'); return; }

    var payload = { usuario: usuario, nombre: nombre, correo: correo || null, password: password || null };
    var btn = document.getElementById('btnGuardarAdmin');
    btn.disabled = true; btn.innerHTML = '<span class="material-symbols-outlined">hourglass_top</span> Guardando...';
    try {
      var url = this.editandoId ? '/api/admin/usuarios/' + this.editandoId : '/api/admin/usuarios';
      var res = await fetch(url, {
        method: this.editandoId ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (localStorage.getItem('abis_token') || '') },
        body: JSON.stringify(payload)
      });
      if (!res.ok) { var err = await res.json().catch(function() { return {}; }); throw new Error(err.error || 'HTTP ' + res.status); }
      window.showToast(this.editandoId ? 'Administrador actualizado' : 'Administrador creado', 'success');
      this.cerrarModal(); this.cargar();
    } catch (e) { window.showToast(e.message, 'error'); }
    finally { btn.disabled = false; btn.innerHTML = '<span class="material-symbols-outlined">save</span> Guardar'; }
  },

  eliminar: async function(id, usuario) {
    if (!confirm('Eliminar al administrador ' + usuario + '?')) return;
    try {
      var res = await fetch('/api/admin/usuarios/' + id, { method: 'DELETE', headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('abis_token') || '') } });
      if (!res.ok) { var err = await res.json().catch(function() { return {}; }); throw new Error(err.error || 'HTTP ' + res.status); }
      window.showToast('Administrador eliminado', 'success');
      this.cargar();
    } catch (e) { window.showToast(e.message, 'error'); }
  },

  esc: function(v) { return String(v||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); },
  escAttr: function(v) { return String(v||'').replace(/[\\'"]/g,'\\$&'); }
};

AdministradoresAdmin.init();
