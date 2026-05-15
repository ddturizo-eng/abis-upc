(function () {
  const fmt = new Intl.NumberFormat('es-CO');
  const token = () => localStorage.getItem('abis_token') || '';

  function authHeaders() {
    return token() ? { Authorization: `Bearer ${token()}` } : {};
  }

  function normalizarLista(payload) {
    if (Array.isArray(payload)) return payload;
    if (Array.isArray(payload?.data)) return payload.data;
    return [];
  }

  function setText(id, value) {
    const element = document.getElementById(id);
    if (element) {
      element.textContent = value;
      element.classList.remove('dashboard-skeleton');
    }
  }

  function porcentaje(parte, total) {
    if (!total) return 0;
    return Math.max(0, Math.min(100, Math.round((parte / total) * 100)));
  }

  function setBar(id, value) {
    const element = document.getElementById(id);
    if (element) element.style.width = `${value}%`;
  }

  function actualizarReloj() {
    const now = new Date();
    setText('live-clock', now.toLocaleTimeString('es-CO', { hour12: false }));
    setText('hero-date', now.toLocaleDateString('es-CO', {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    }));
  }

  function updateDonutChart(percentage) {
    const fill = document.getElementById('donut-fill');
    if (!fill) return;
    const circumference = 2 * Math.PI * 54;
    const offset = circumference - (percentage / 100) * circumference;
    fill.style.strokeDasharray = circumference;
    fill.style.strokeDashoffset = offset;
  }

  async function loadStats() {
    try {
      const response = await fetch('/api/votantes/estadisticas', { headers: authHeaders() });
      if (!response.ok) throw new Error('Error al cargar estadísticas');
      const data = await response.json();
      const total = Number(data.total || 0);
      const votaron = Number(data.votaron || data.ejercidos || 0);
      const pendientes = Number(data.pendientes || 0);
      const pctVotaron = porcentaje(votaron, total);
      const pctPendientes = porcentaje(pendientes, total);

      setText('total-habilitados', fmt.format(total));
      setText('han-votado', fmt.format(votaron));
      setText('pendientes', fmt.format(pendientes));
      setText('pct-participacion', `${pctVotaron}%`);
      setText('pct-votaron', `${pctVotaron}%`);
      setText('pct-pendientes', `${pctPendientes}%`);
      setBar('bar-votaron', pctVotaron);
      setBar('bar-pendientes', pctPendientes);
      updateDonutChart(pctVotaron);
    } catch (error) {
      setText('total-habilitados', 'Error');
      setText('han-votado', '0');
      setText('pendientes', '0');
      setText('pct-participacion', '0%');
      updateDonutChart(0);
    }
  }

  async function loadNextElection() {
    try {
      const response = await fetch('/api/elecciones?estado=PROGRAMADA', { headers: authHeaders() });
      if (!response.ok) throw new Error('Error al cargar elecciones');
      const payload = await response.json();
      const elecciones = normalizarLista(payload).sort((a, b) =>
        new Date(a.fechaHoraInicio || a.fecha_hora_inicio || 0) - new Date(b.fechaHoraInicio || b.fecha_hora_inicio || 0)
      );
      const election = elecciones[0];

      if (!election) {
        setText('eleccion-nombre', 'Sin elecciones programadas');
        setText('eleccion-fecha', 'No hay fecha asignada');
        setText('eleccion-descripcion', 'Crea o programa una elección para iniciar la gestión.');
        const badge = document.getElementById('eleccion-estado');
        if (badge) {
          badge.textContent = 'Sin agenda';
          badge.className = 'status-pill status-cerrada';
        }
        return;
      }

      const estado = election.estado || 'PROGRAMADA';
      const fecha = election.fechaHoraInicio || election.fecha_hora_inicio;
      setText('eleccion-nombre', election.nombre || 'Elección programada');
      setText('eleccion-fecha', fecha ? new Date(fecha).toLocaleDateString('es-CO', {
        weekday: 'long',
        year: 'numeric',
        month: 'long',
        day: 'numeric'
      }) + '  ·  ' + new Date(fecha).toLocaleTimeString('es-CO', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: true
      }) : 'Fecha pendiente');
      setText('eleccion-descripcion', 'Prepárate para gestionar la jornada electoral');
      const badge = document.getElementById('eleccion-estado');
      if (badge) {
        badge.textContent = estado.replace('_', ' ');
        badge.className = `status-pill ${estado === 'EN_CURSO' ? 'status-en-curso' : estado === 'CERRADA' ? 'status-cerrada' : 'status-programada'}`;
      }
    } catch (error) {
      setText('eleccion-nombre', 'Error al cargar');
      setText('eleccion-fecha', 'No fue posible consultar elecciones');
    }
  }

  async function loadRecentActivity() {
    try {
      const response = await fetch('/api/auditoria/reciente?limit=3', { headers: authHeaders() });
      if (!response.ok) throw new Error('Error al cargar actividad');
      const payload = await response.json();
      const items = normalizarLista(payload);
      renderActivity(items);
    } catch (error) {
      renderActivity([]);
    }
  }

  function renderActivity(items) {
    const container = document.getElementById('actividad-dashboard');
    if (!container) return;

    const defaults = [
      {
        label: 'Último registro',
        icon: 'groups',
        avatar: 'activity-avatar-green',
        name: 'Sin registros recientes',
        time: 'Actividad pendiente'
      },
      {
        label: 'Última autenticación',
        icon: 'fingerprint',
        avatar: 'activity-avatar-purple',
        name: 'Sin autenticaciones',
        time: 'Actividad pendiente'
      },
      {
        label: 'Último certificado',
        icon: 'badge',
        avatar: 'activity-avatar-yellow',
        name: 'Sin certificados',
        time: 'Actividad pendiente'
      }
    ];

    const cards = defaults.map((base, index) => {
      const item = items[index];
      if (!item) return base;
      return {
        ...base,
        name: item.accion === 'RE_ENROLAMIENTO'
          ? `Votante ${item.identificacion || '--'}`
          : (item.nombreAdmin || `Votante ${item.identificacion || '--'}`),
        time: tiempoRelativo(item.fechaHora)
      };
    });

    container.innerHTML = cards.map((card) => `
      <div class="activity-item">
        <span class="activity-avatar ${card.avatar} material-symbols-outlined">${card.icon}</span>
        <div>
          <p class="activity-label">${card.label}</p>
          <p class="activity-name">${card.name}</p>
          <p class="activity-time">${card.time}</p>
        </div>
      </div>
    `).join('');
  }

  function tiempoRelativo(value) {
    if (!value) return 'Hace unos instantes';
    const minutes = Math.max(1, Math.round((Date.now() - new Date(value).getTime()) / 60000));
    if (minutes < 60) return `Hace ${minutes} minutos`;
    const hours = Math.round(minutes / 60);
    return `Hace ${hours} horas`;
  }

  function init() {
    if (window.dashboardClock) clearInterval(window.dashboardClock);
    actualizarReloj();
    window.dashboardClock = setInterval(actualizarReloj, 1000);
    updateDonutChart(0);
    loadStats();
    loadNextElection();
    loadRecentActivity();
  }

  init();
})();
