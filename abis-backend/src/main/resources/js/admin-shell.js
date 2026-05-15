async function cargarComponente(id, url) {
      const target = document.getElementById(id);
      if (!target) return;
      const response = await fetch(url);
      target.innerHTML = await response.text();
    }

    async function cargarComponentesAdmin() {
      await Promise.all([
        cargarComponente('admin-header', '/components/header.html'),
        cargarComponente('admin-bottom-nav', '/components/bottom-nav.html')
      ]);

      if (window.AdminRouter) {
        const seccion = window.location.hash.replace('#', '') || 'dashboard';
        AdminRouter.actualizarNavbar(seccion);
      }

      actualizarEstadoOracleHeader();
      inicializarMenuAdministrador();
    }

    async function actualizarEstadoOracleHeader() {
      try {
        const data = await API.get('/api/health');
        const ok = data.database === 'ok';
        const badge = document.getElementById('admin-oracle-status');
        if (badge) {
          badge.innerHTML = `Oracle XE <span class="${ok ? 'admin-status-dot' : 'text-red-500'}">â—</span>`;
        }
      } catch (error) {
        const badge = document.getElementById('admin-oracle-status');
        if (badge) {
          badge.innerHTML = 'Oracle XE <span class="text-red-500">â—</span>';
        }
      }
    }

    cargarComponentesAdmin();

    function inicializarMenuAdministrador() {
      const button = document.getElementById('admin-profile-button');
      const dropdown = document.getElementById('admin-profile-dropdown');
      if (!button || !dropdown) return;

      const user = leerUsuarioAdmin();
      document.querySelectorAll('.admin-profile-name, .admin-profile-summary-name').forEach((element) => {
        element.textContent = user.nombre;
      });
      document.querySelectorAll('.admin-avatar').forEach((element) => {
        element.textContent = user.iniciales;
      });

      button.addEventListener('click', (event) => {
        event.stopPropagation();
        const abierto = dropdown.classList.toggle('open');
        button.setAttribute('aria-expanded', String(abierto));
      });

      dropdown.addEventListener('click', (event) => {
        event.stopPropagation();
      });

      document.addEventListener('click', () => cerrarMenuAdministrador());
      document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') cerrarMenuAdministrador();
      });
    }

    function cerrarMenuAdministrador() {
      const button = document.getElementById('admin-profile-button');
      const dropdown = document.getElementById('admin-profile-dropdown');
      if (!button || !dropdown) return;
      dropdown.classList.remove('open');
      button.setAttribute('aria-expanded', 'false');
    }

    function leerUsuarioAdmin() {
      const fallback = { nombre: 'Administrador', iniciales: 'AD' };
      try {
        const raw = localStorage.getItem('abis_user');
        const data = raw ? JSON.parse(raw) : {};
        const nombre = data.nombre || data.usuario || localStorage.getItem('abis_admin_nombre') || fallback.nombre;
        const iniciales = nombre
          .split(/\s+/)
          .filter(Boolean)
          .slice(0, 2)
          .map((parte) => parte.charAt(0).toUpperCase())
          .join('') || fallback.iniciales;
        return { nombre, iniciales };
      } catch (error) {
        return fallback;
      }
    }

    function verPerfilAdministrador() {
      cerrarMenuAdministrador();
      alert('Perfil del administrador prÃ³ximamente.');
    }

    async function cerrarSesion() {
      cerrarMenuAdministrador();
      const token = localStorage.getItem('abis_token');
      if (token) {
        try {
          await fetch('/api/auth/logout', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': 'Bearer ' + token
            },
            body: JSON.stringify({ token })
          });
        } catch (error) {
          console.warn('No fue posible cerrar la sesiÃ³n en el servidor:', error);
        }
      }
      localStorage.removeItem('abis_token');
      localStorage.removeItem('abis_user');
      localStorage.removeItem('abis_admin_nombre');
      window.location.href = '/pages/auth/login.html';
    }

