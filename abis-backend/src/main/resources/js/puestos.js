const PuestosAdmin = {
  editandoId: null,

  init() {
    this.cargar();
    document.getElementById('formPuesto').addEventListener('submit', (e) => this.guardar(e));
  },

  formatearFecha(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return d.toLocaleDateString('es-CO', { day: '2-digit', month: '2-digit', year: 'numeric' }) +
      ' ' + d.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });
  },

  fechaLocal(iso) {
    if (!iso) return '';
    try {
      const d = new Date(iso);
      if (isNaN(d.getTime())) return iso;
      const pad = (n) => String(n).padStart(2, '0');
      return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
        'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    } catch (_) { return iso; }
  },

  async cargar() {
    const tbody = document.getElementById('puestos-tbody');
    tbody.innerHTML = Array.from({ length: 4 }, () => '<tr><td colspan="7"><div class="dashboard-skeleton h-[46px]"></div></td></tr>').join('');
    try {
      const response = await fetch('/api/puestos');
      if (!response.ok) throw new Error('Error al cargar puestos');
      const data = await response.json();
      this.renderizar(data);
    } catch (error) {
      tbody.innerHTML = '<tr><td colspan="7" class="text-center"><p class="dashboard-error">Error al cargar puestos de votacion</p></td></tr>';
      if (window.showToast) window.showToast(error.message || 'Error al cargar puestos', 'error');
    }
  },

  renderizar(puestos) {
    const tbody = document.getElementById('puestos-tbody');
    if (!puestos || puestos.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" class="text-center"><p class="dashboard-empty">No hay puestos de votacion registrados</p></td></tr>';
      return;
    }
    tbody.innerHTML = puestos.map(p => {
      const id = p.id || p.idPuesto || '';
      const nombre = p.nombrePuesto || p.nombre_puesto || '—';
      const ciudad = p.ciudad || '—';
      const sede = p.sede || '—';
      const inicio = this.formatearFecha(p.horaInicio || p.hora_inicio);
      const salida = this.formatearFecha(p.horaSalida || p.hora_salida);
      return `<tr>
        <td>${id}</td>
        <td>${this.esc(nombre)}</td>
        <td>${this.esc(ciudad)}</td>
        <td>${this.esc(sede)}</td>
        <td>${inicio}</td>
        <td>${salida}</td>
        <td>
          <button class="cert-btn cert-btn-ghost" onclick="PuestosAdmin.editar(${id})" title="Editar"><span class="material-symbols-outlined">edit</span></button>
          <button class="cert-btn cert-btn-ghost" onclick="PuestosAdmin.eliminar(${id})" title="Eliminar"><span class="material-symbols-outlined">delete</span></button>
        </td>
      </tr>`;
    }).join('');
  },

  abrirModal(puesto) {
    const modal = document.getElementById('modalPuesto');
    const titulo = document.getElementById('modalPuestoTitulo');
    document.getElementById('formPuesto').reset();
    document.getElementById('puestoId').value = '';

    if (puesto) {
      this.editandoId = puesto.id || puesto.idPuesto;
      titulo.textContent = 'Editar puesto de votacion';
      document.getElementById('puestoCiudad').value = puesto.ciudad || '';
      document.getElementById('puestoSede').value = puesto.sede || 'SEDE CENTRAL';
      document.getElementById('puestoNombre').value = puesto.nombrePuesto || puesto.nombre_puesto || '';
      document.getElementById('puestoInicio').value = this.fechaLocale(puesto.horaInicio || puesto.hora_inicio);
      document.getElementById('puestoSalida').value = this.fechaLocale(puesto.horaSalida || puesto.hora_salida);
    } else {
      this.editandoId = null;
      titulo.textContent = 'Nuevo puesto de votacion';
    }

    modal.classList.add('open');
    modal.removeAttribute('inert');
  },

  cerrarModal() {
    const modal = document.getElementById('modalPuesto');
    modal.classList.remove('open');
    modal.setAttribute('inert', '');
    this.editandoId = null;
  },

  async editar(id) {
    try {
      const response = await fetch('/api/puestos');
      const data = await response.json();
      const puesto = data.find(p => (p.id || p.idPuesto) == id);
      if (puesto) this.abrirModal(puesto);
    } catch (error) {
      if (window.showToast) window.showToast('No fue posible cargar el puesto', 'error');
    }
  },

  async guardar(event) {
    event.preventDefault();
    const payload = {
      ciudad: document.getElementById('puestoCiudad').value.trim(),
      sede: document.getElementById('puestoSede').value,
      nombrePuesto: document.getElementById('puestoNombre').value.trim(),
      horaInicio: document.getElementById('puestoInicio').value,
      horaSalida: document.getElementById('puestoSalida').value
    };

    if (!payload.ciudad || !payload.sede || !payload.nombrePuesto || !payload.horaInicio || !payload.horaSalida) {
      if (window.showToast) window.showToast('Todos los campos son obligatorios', 'error');
      return;
    }

    if (payload.horaInicio >= payload.horaSalida) {
      if (window.showToast) window.showToast('La hora de salida debe ser posterior a la de inicio', 'error');
      return;
    }

    try {
      const url = this.editandoId ? `/api/puestos/${this.editandoId}` : '/api/puestos';
      const method = this.editandoId ? 'PUT' : 'POST';
      const response = await fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (localStorage.getItem('abis_token') || '') },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        const err = await response.json().catch(() => ({}));
        throw new Error(err.error || err.message || `HTTP ${response.status}`);
      }

      if (window.showToast) window.showToast(this.editandoId ? 'Puesto actualizado' : 'Puesto creado', 'success');
      this.cerrarModal();
      this.cargar();
    } catch (error) {
      if (window.showToast) window.showToast(error.message || 'No fue posible guardar el puesto', 'error');
    }
  },

  async eliminar(id) {
    if (!confirm('Eliminar este puesto de votacion?')) return;
    try {
      const response = await fetch(`/api/puestos/${id}`, {
        method: 'DELETE',
        headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('abis_token') || '') }
      });

      if (!response.ok) {
        const err = await response.json().catch(() => ({}));
        throw new Error(err.error || err.message || `HTTP ${response.status}`);
      }

      if (window.showToast) window.showToast('Puesto eliminado', 'success');
      this.cargar();
    } catch (error) {
      if (window.showToast) window.showToast(error.message || 'No fue posible eliminar el puesto', 'error');
    }
  },

  fechaLocale(iso) {
    if (!iso) return '';
    try {
      const d = new Date(iso);
      if (isNaN(d.getTime())) return iso;
      const pad = (n) => String(n).padStart(2, '0');
      return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
        'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    } catch (_) { return iso; }
  },

  esc(v) {
    return String(v || '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[c]));
  }
};

PuestosAdmin.init();
