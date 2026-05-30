/**
 * Escapa caracteres HTML especiales para prevenir inyección de contenido.
 *
 * Se utiliza antes de insertar datos dinámicos en plantillas HTML
 * generadas mediante template strings, evitando la ejecución de
 * código no confiable proveniente de almacenamiento local o APIs.
 *
 * @param {*} v Valor a convertir de forma segura.
 * @returns {string} Cadena escapada para renderizado HTML.
 */

const eHtml = (v) => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]));

/**
 * Carga dinámicamente un componente HTML dentro de un contenedor.
 *
 * Permite reutilizar fragmentos compartidos de interfaz como
 * encabezados, barras de navegación y componentes institucionales
 * sin duplicar código entre páginas.
 *
 * @param {string} id Identificador del elemento contenedor.
 * @param {string} url Ruta del componente HTML.
 * @returns {Promise<void>}
 */

async function cargarComponente(id, url) {
      const target = document.getElementById(id);
      if (!target) return;
      const response = await fetch(url);
      target.innerHTML = await response.text();
    }

    async function cargarComponentesAdmin() {
      await Promise.all([
        cargarComponente('admin-header', '/components/header.html')
        // Dock flotante desactivado temporalmente. Reactivar junto con el contenedor en pages/admin/index.html.
        // cargarComponente('admin-bottom-nav', '/components/bottom-nav.html')
      ]);

      if (window.AdminRouter) {
        const seccion = window.location.hash.replace('#', '') || 'dashboard';
        AdminRouter.actualizarNavbar(seccion);
      }

      inicializarMenuAdministrador();
    }
/**
 * Inicializa los componentes visuales del panel administrativo.
 *
 * Carga los elementos compartidos de la interfaz y sincroniza
 * el estado visual de navegación con la sección actualmente
 * seleccionada dentro del router administrativo.
 *
 * @returns {Promise<void>}
 */
    cargarComponentesAdmin();
/**
 * Configura el menú desplegable del perfil administrativo.
 *
 * Asigna la información del usuario autenticado a los elementos
 * visuales del encabezado y registra los eventos necesarios para
 * apertura, cierre y navegación del menú contextual.
 */

    function inicializarMenuAdministrador() {
      const button = document.getElementById('admin-profile-button');
      const dropdown = document.getElementById('admin-profile-dropdown');
      if (!button || !dropdown) return;

      const user = leerUsuarioAdmin();
      document.querySelectorAll('.admin-profile-username, .admin-profile-summary-name').forEach((element) => {
        element.textContent = user.usuario;
      });
      document.querySelectorAll('.admin-profile-role, .admin-profile-summary-role').forEach((element) => {
        element.textContent = user.rol;
      });
      document.querySelectorAll('.admin-profile-fullname').forEach((element) => {
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
/**
 * Cierra el menú desplegable del perfil administrativo.
 *
 * Restablece el estado visual del componente y actualiza los
 * atributos de accesibilidad utilizados por lectores de pantalla.
 */

    function cerrarMenuAdministrador() {
      const button = document.getElementById('admin-profile-button');
      const dropdown = document.getElementById('admin-profile-dropdown');
      if (!button || !dropdown) return;
      dropdown.classList.remove('open');
      button.setAttribute('aria-expanded', 'false');
    }

    const notifBell = document.getElementById('notif-bell');
    const notifDropdown = document.getElementById('notif-dropdown');
    if (notifBell && notifDropdown) {
      notifBell.addEventListener('click', (e) => {
        e.stopPropagation();
        notifDropdown.classList.toggle('open');
      });
      document.addEventListener('click', () => notifDropdown.classList.remove('open'));
    }

/**
 * Obtiene la información del administrador autenticado.
 *
 * Recupera los datos almacenados localmente durante el proceso
 * de autenticación. Si la información no existe o se encuentra
 * corrupta, retorna valores por defecto para mantener la
 * estabilidad de la interfaz.
 *
 * @returns {{
 *   usuario: string,
 *   nombre: string,
 *   rol: string,
 *   iniciales: string
 * }}
 */

    function leerUsuarioAdmin() {
      const fallback = { usuario: 'abisadmin', nombre: 'Administrador', rol: 'Administrador', iniciales: 'AD' };
      try {
        const raw = localStorage.getItem('abis_user');
        const data = raw ? JSON.parse(raw) : {};
        const usuario = data.usuario || localStorage.getItem('abis_admin_usuario') || fallback.usuario;
        const nombre = data.nombre || localStorage.getItem('abis_admin_nombre') || fallback.nombre;
        const rol = data.rol || fallback.rol;
        const iniciales = (nombre || usuario)
          .split(/\s+/)
          .filter(Boolean)
          .slice(0, 2)
          .map((parte) => parte.charAt(0).toUpperCase())
          .join('') || fallback.iniciales;
        return { usuario, nombre, rol, iniciales };
      } catch (error) {
        return fallback;
      }
    }
/**
 * Muestra la ventana modal con la información del administrador.
 *
 * Construye dinámicamente la interfaz de perfil cuando aún no
 * existe en el DOM y reutiliza la instancia creada en accesos
 * posteriores para reducir manipulaciones innecesarias.
 */
    function verPerfilAdministrador() {
      cerrarMenuAdministrador();
      const user = leerUsuarioAdmin();
      let modal = document.getElementById('admin-profile-modal');
      if (!modal) {
        modal = document.createElement('div');
        modal.id = 'admin-profile-modal';
        modal.className = 'admin-profile-overlay';
        modal.innerHTML = `
          <div class="admin-profile-card">
            <div class="admin-profile-card-header">
              <span class="admin-profile-card-avatar">${eHtml(user.iniciales)}</span>
              <div>
                <h2>${eHtml(user.nombre)}</h2>
                <p>${eHtml(user.rol)}</p>
              </div>
              <button class="admin-profile-card-close" onclick="document.getElementById('admin-profile-modal').classList.add('hidden')">&times;</button>
            </div>
            <div class="admin-profile-card-body">
              <div class="admin-profile-field">
                <span class="admin-profile-field-icon"><span class="material-symbols-outlined">badge</span></span>
                <div><small>Usuario</small><strong>${eHtml(user.usuario)}</strong></div>
              </div>
              <div class="admin-profile-field">
                <span class="admin-profile-field-icon"><span class="material-symbols-outlined">shield_person</span></span>
                <div><small>Rol</small><strong>${eHtml(user.rol)}</strong></div>
              </div>
              <div class="admin-profile-field">
                <span class="admin-profile-field-icon"><span class="material-symbols-outlined">schedule</span></span>
                <div><small>Sesion iniciada</small><strong id="admin-session-time">Activa</strong></div>
              </div>
            </div>
            <div class="admin-profile-card-actions">
              <button class="admin-profile-btn" onclick="cerrarSesion()"><span class="material-symbols-outlined">logout</span> Cerrar sesion</button>
            </div>
          </div>
        `;
        document.body.appendChild(modal);
        modal.addEventListener('click', (e) => { if (e.target === modal) modal.classList.add('hidden'); });
      }
      modal.classList.remove('hidden');
    }
/**
 * Finaliza la sesión administrativa activa.
 *
 * Intenta notificar al servidor para invalidar el token de acceso.
 * Independientemente del resultado de la comunicación, elimina las
 * credenciales almacenadas localmente y redirige al formulario
 * de autenticación.
 *
 * @returns {Promise<void>}
 */
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
          console.warn('No fue posible cerrar la sesión en el servidor:', error);
        }
      }
      localStorage.removeItem('abis_token');
      localStorage.removeItem('abis_user');
      localStorage.removeItem('abis_admin_nombre');
      localStorage.removeItem('abis_admin_usuario');
      window.location.href = '/pages/auth/login.html';
    }

