/**
 * Router principal del panel administrativo.
 *
 * Centraliza la navegación entre los módulos de administración mediante
 * carga dinámica de vistas HTML. Este enfoque evita recargas completas
 * de la aplicación y mantiene una experiencia consistente para el usuario.
 *
 * También controla la sincronización entre la URL, el estado visual de
 * la navegación y el contenido mostrado en pantalla.
 */
const AdminRouter = {
  /**
   * Secciones habilitadas dentro del panel administrativo.
   *
   * La lista actúa como mecanismo de validación para impedir la carga
   * de rutas arbitrarias mediante manipulación manual de la URL.
   */

  secciones: ['dashboard', 'registro', 'votantes', 'elecciones', 'candidatos', 'jurados', 'votacion', 'contingencia', 'certificados'],

  /**
   * Inicializa el router utilizando la sección indicada en el hash.
   *
   * Si la ruta solicitada no pertenece al conjunto de módulos
   * autorizados, se redirige automáticamente al dashboard para
   * mantener un estado válido de navegación.
   */

  init() {
    const hash = window.location.hash.replace('#', '');
    const seccion = this.secciones.includes(hash) ? hash : 'dashboard';
    this.irA(seccion);
  },

  /**
   * Navega hacia un módulo administrativo.
   *
   * Carga dinámicamente la vista correspondiente, actualiza el
   * estado visual de navegación y sincroniza la URL con la
   * sección actualmente mostrada.
   *
   * @param {string} seccion Identificador del módulo a cargar.
   * @returns {Promise<void>}
   */

  async irA(seccion) {
    if (seccion === 'registro') {
      window.location.href = '/pages/registro/index.html';
      return;
    }

    if (!this.secciones.includes(seccion)) {
      seccion = 'dashboard';
    }

    const contenedor = document.getElementById('admin-content');
    if (!contenedor) return;

    try {
      const response = await fetch(`/pages/admin/${seccion}.html`);
      if (!response.ok) {
        throw new Error(`No se pudo cargar ${seccion}`);
      }

      contenedor.innerHTML = await response.text();
      this.ejecutarScripts(contenedor);
      this.actualizarNavbar(seccion);
      this.actualizarTitulo(seccion);
      window.location.hash = `#${seccion}`;
    } catch (error) {
      contenedor.innerHTML = `
        <div class="admin-section">
          <div class="section-header">
            <h1 class="section-title">Error</h1>
            <p class="section-subtitle">No fue posible cargar el módulo solicitado.</p>
          </div>
        </div>
      `;
    }
  },

  /**
   * Actualiza el estado visual de la barra de navegación.
   *
   * Mantiene sincronizados los elementos de navegación de escritorio
   * y móvil, resaltando únicamente el módulo actualmente activo.
   *
   * @param {string} seccion Sección seleccionada por el usuario.
   */

  actualizarNavbar(seccion) {
    const activeClass = "flex flex-col items-center text-[#edfaf3] scale-110 after:content-['•'] after:text-[#edfaf3] after:text-[14px] after:-mt-1 font-dm nav-item active";
    const inactiveClass = "flex flex-col items-center text-white/60 hover:text-white transition-all font-dm nav-item";

    document.querySelectorAll('.admin-navbar .nav-item').forEach((item) => {
      item.className = item.dataset.section === seccion ? activeClass : inactiveClass;
    });

    document.querySelectorAll('.admin-header-nav .admin-header-nav-item').forEach((item) => {
      item.classList.toggle('active', item.dataset.section === seccion);
    });
  },

  /**
   * Actualiza el título principal mostrado en la interfaz administrativa.
   *
   * Los títulos se desacoplan de los identificadores internos de ruta
   * para permitir nombres más descriptivos orientados al usuario final.
   *
   * @param {string} seccion Sección actualmente activa.
   */

  actualizarTitulo(seccion) {
    const titulos = {
      dashboard: 'Dashboard',
      votantes: 'Censo Electoral',
      elecciones: 'Gestión de Elecciones',
      candidatos: 'Candidatos',
      jurados: 'Mesas y Jurados',
      votacion: 'Modo de Votacion',
      contingencia: 'QR de Contingencia',
      certificados: 'Certificados de Participación'
    };
    const titulo = document.getElementById('admin-section-title');
    if (titulo) {
      titulo.textContent = titulos[seccion] || 'Panel de Administración';
    }
  },

  /**
   * Ejecuta los scripts incluidos en una vista cargada dinámicamente.
   *
   * Los elementos script insertados mediante innerHTML no se ejecutan
   * automáticamente en el navegador. Este procedimiento recrea cada script
   * para garantizar la inicialización correcta del módulo recién cargado.
   *
   * @param {HTMLElement} contenedor Contenedor donde fue insertada la vista.
   */

  ejecutarScripts(contenedor) {
    contenedor.querySelectorAll('script').forEach((scriptOriginal) => {
      const script = document.createElement('script');
      Array.from(scriptOriginal.attributes).forEach((attr) => {
        script.setAttribute(attr.name, attr.value);
      });
      script.textContent = scriptOriginal.textContent;
      document.body.appendChild(script);
      if (script.src) {
        script.addEventListener('load', () => script.remove(), { once: true });
        script.addEventListener('error', () => script.remove(), { once: true });
      } else {
        script.remove();
      }
    });
  },

  /**
   * Verifica la existencia de una sesión administrativa válida.
   *
   * El acceso al panel administrativo requiere un token previamente
   * emitido por el proceso de autenticación. Si el token no existe,
   * se redirige inmediatamente a la pantalla de inicio de sesión.
   *
   * @returns {string|null} Token de autenticación o null si no existe.
   */

  verificarAuth() {
    const token = localStorage.getItem('abis_token');
    if (!token) {
      window.location.replace('/pages/auth/login.html');
      return null;
    }
    return token;
  }
};

/**
 * Punto de entrada del módulo.
 *
 * La inicialización del router ocurre únicamente cuando existe una
 * sesión autenticada válida, evitando exponer funcionalidades
 * administrativas a usuarios no autorizados.
 */

window.AdminRouter = AdminRouter;

if (AdminRouter.verificarAuth()) {
  AdminRouter.init();
}
