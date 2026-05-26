const AdministradoresAdmin = {
  editandoId: null,

  init() {
    this.cargar();
    document.getElementById('formAdmin').addEventListener('submit', (e) => this.guardar(e));
  },

  async cargar() {
    const tbody = document.getElementById('admins-tbody');
    tbody.innerHTML = Array.from({ length: 3 }, () => '<tr><td colspan="5"><div class="dashboard-skeleton h-[46px]"></div></td></tr>').join('');
    try {
      const response = await fetch('/api/admin/usuarios', {
        headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('abis_token') || '') }
      });
      if (!response.ok) throw new Error('Error al cargar administradores');
      const result = await response.json();
      const data = result.data || result;
      this.renderizar(Array.isArray(data) ? data : []);
    } catch (error) {
      tbody.innerHTML = '<tr><td colspan="5" class="text-center"><p class="dashboard-error">Error al cargar administradores</p></td></tr>';
      if (window.showToast) window.showToast(error.message || 'Error al cargar', 'error');
    }
  },

  renderizar(admins) {
    const tbody = document.getElementById('admins-tbody');
    if (!admins.length) {
      tbody.innerHTML = '<tr><td colspan="5" class="text-center"><p class="dashboard-empty">No hay administradores registrados</p></td></tr>';
      return;
    }
    tbody.innerHTML = admins.map(a => `<tr>
      <td>${a.id || '—'}</td>
      <td><strong>${this.esc(a.usuario)}</strong></td>
      <td>${this.esc(a.nombre)}</td>
      <td>${this.esc(a.correo || '—')}</td>
      <td>
        <button class="cert-btn cert-btn-ghost" onclick="AdministradoresAdmin.editar(${a.id})" title="Editar"><span class="material-symbols-outlined">edit</span></button>
        <button class="cert-btn cert-btn-ghost" onclick="AdministradoresAdmin.eliminar(${a.id},'${this.escAttr(a.usuario)}')" title="Eliminar"><span class="material-symbols-outlined">delete</span></button>
      </td>
    </tr>`).join('');
  },

  abrirModal(admin) {
    const modal = document.getElementById('modalAdmin');
    document.getElementById('formAdmin').reset();
    document.getElementById('adminId').value = '';
    document.getElementById('adminPasswordLabel').textContent = 'Contrasena *';

    if (admin) {
      this.editandoId = admin.id;
      document.getElementById('modalAdminTitulo').textContent = 'Editar administrador';
      document.getElementById('adminUsuario').value = admin.usuario || '';
      document.getElementById('adminUsuario').disabled = true;
      document.getElementById('adminNombre').value = admin.nombre || '';
      document.getElementById('adminCorreo').value = admin.correo || '';
      document.getElementById('adminPasswordLabel').textContent = 'Nueva contrasena (dejar vacio para no cambiar)';
    } else {
      this.editandoId = null;
      document.getElementById('modalAdminTitulo').textContent = 'Nuevo administrador';
      document.getElementById('adminUsuario').disabled = false;
    }

    modal.classList.add('open');
    modal.removeAttribute('inert');
  },

  cerrarModal() {
    const modal = document.getElementById('modalAdmin');
    modal.classList.remove('open');
    modal.setAttribute('inert', '');
    this.editandoId = null;
  },

  async editar(id) {
    try {
      const response = await fetch('/api/admin/usuarios', {
        headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('abis_token') || '') }
      });
      const result = await response.json();
      const data = result.data || result;
      const admin = (Array.isArray(data) ? data : []).find(a => a.id == id);
      if (admin) this.abrirModal(admin);
    } catch (error) {
      if (window.showToast) window.showToast('No fue posible cargar el administrador', 'error');
    }
  },

  async guardar(event) {
    event.preventDefault();
    const usuario = document.getElementById('adminUsuario').value.trim();
    const nombre = document.getElementById('adminNombre').value.trim();
    const correo = document.getElementById('adminCorreo').value.trim();
    const password = document.getElementById('adminPassword').value;

    if (!usuario) { window.showToast('Usuario requerido', 'error'); return; }
    if (!nombre) { window.showToast('Nombre requerido', 'error'); return; }
    if (!this.editandoId && !password) { window.showToast('Contrasena requerida', 'error'); return; }
    if (correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo)) {
      window.showToast('Formato de correo invalido', 'error'); return;
    }

    const payload = { usuario, nombre, correo: correo || null, password: password || null };

    try {
      const url = this.editandoId ? `/api/admin/usuarios/${this.editandoId}` : '/api/admin/usuarios';
      const method = this.editandoId ? 'PUT' : 'POST';
      const response = await fetch(url, {
        method: method,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + (localStorage.getItem('abis_token') || '')
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        const err = await response.json().catch(() => ({}));
        throw new Error(err.error || err.message || `HTTP ${response.status}`);
      }

      window.showToast(this.editandoId ? 'Administrador actualizado' : 'Administrador creado', 'success');
      this.cerrarModal();
      this.cargar();
    } catch (error) {
      window.showToast(error.message || 'No fue posible guardar', 'error');
    }
  },

  async eliminar(id, usuario) {
    if (!confirm('Eliminar al administrador ' + usuario + '?')) return;
    try {
      const response = await fetch(`/api/admin/usuarios/${id}`, {
        method: 'DELETE',
        headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('abis_token') || '') }
      });

      if (!response.ok) {
        const err = await response.json().catch(() => ({}));
        throw new Error(err.error || err.message || `HTTP ${response.status}`);
      }

      window.showToast('Administrador eliminado', 'success');
      this.cargar();
    } catch (error) {
      window.showToast(error.message || 'No fue posible eliminar', 'error');
    }
  },

  esc(v) {
    return String(v || '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[c]));
  },

  escAttr(v) {
    return String(v || '').replace(/[\\'"]/g, '\\$&');
  }
};

AdministradoresAdmin.init();
