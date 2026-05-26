const AdminRouter = {
  secciones: ['dashboard', 'registro', 'votantes', 'elecciones', 'candidatos', 'jurados', 'puestos', 'usuarios', 'votacion', 'contingencia', 'certificados'],

  init() {
    const hash = window.location.hash.replace('#', '');
    const seccion = this.secciones.includes(hash) ? hash : 'dashboard';
    this.irA(seccion);
  },

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

  actualizarTitulo(seccion) {
    const titulos = {
      dashboard: 'Dashboard',
      votantes: 'Censo Electoral',
      elecciones: 'Gestión de Elecciones',
      candidatos: 'Candidatos',
      jurados: 'Mesas y Jurados',
      puestos: 'Puestos de Votacion',
      usuarios: 'Administradores',
      votacion: 'Modo de Votacion',
      contingencia: 'QR de Contingencia',
      certificados: 'Certificados de Participación'
    };
    const titulo = document.getElementById('admin-section-title');
    if (titulo) {
      titulo.textContent = titulos[seccion] || 'Panel de Administración';
    }
  },

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

  verificarAuth() {
    const token = localStorage.getItem('abis_token');
    if (!token) {
      window.location.replace('/pages/auth/login.html');
      return null;
    }
    return token;
  }
};

window.AdminRouter = AdminRouter;

if (AdminRouter.verificarAuth()) {
  AdminRouter.init();
}
