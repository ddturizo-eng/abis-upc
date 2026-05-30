/**
 * Inicializa los comportamientos visuales del módulo de votación.
 *
 * Centraliza las animaciones, indicadores de progreso y estados
 * visuales del kiosco electoral para mantener una experiencia
 * consistente durante las etapas de verificación e interacción
 * con el tarjetón electrónico.
 */

(function initVotacionVisual() {
  const $ = (id) => document.getElementById(id);
  /**
   * Actualiza el indicador circular de progreso.
   *
   * Utiliza la circunferencia del SVG para representar visualmente
   * el avance del proceso de identificación y votación sin depender
   * de componentes gráficos externos.
   *
   * @param {number} percent Porcentaje de progreso (0-100).
   */
  function setProgress(percent) {
    const circle = document.querySelector('.fingerprint-progress circle:last-child');
    if (!circle) return;
    const radius = Number(circle.getAttribute('r') || 102);
    const circumference = 2 * Math.PI * radius;
    circle.style.strokeDasharray = String(circumference);
    circle.style.strokeDashoffset = String(circumference);
    requestAnimationFrame(() => {
      circle.style.strokeDashoffset = String(circumference - (circumference * percent) / 100);
    });
  }
  /**
   * Actualiza el estado visual de las etapas del proceso.
   *
   * Permite mostrar claramente al votante la etapa actual,
   * las etapas completadas y las pendientes, reduciendo
   * incertidumbre durante el flujo de votación.
   *
   * @param {number} index Índice de la etapa activa.
   */
  function setStep(index) {
    document.querySelectorAll('.verify-step').forEach((step, position) => {
      step.classList.toggle('is-active', position === index);
      step.classList.toggle('is-done', position < index);
      step.classList.toggle('active', position === index);
    });
  }
  /**
   * Configura la retroalimentación visual de inicio.
   *
   * Proporciona una respuesta inmediata al usuario cuando
   * solicita comenzar el proceso de verificación, evitando
   * la percepción de inactividad mientras se cargan recursos.
   */
  function initWelcomeFeedback() {
    const button = $('btn-go-verify');
    if (!button) return;
    button.addEventListener('click', () => {
      button.classList.add('is-loading');
      const icon = button.querySelector('i');
      if (icon) icon.className = 'ti ti-loader-2';
    });
  }
  /**
   * Inicializa los indicadores visuales asociados al proceso
   * de verificación de identidad.
   *
   * Mantiene sincronizados los controles de la interfaz con
   * el estado esperado del flujo biométrico para facilitar
   * el seguimiento por parte del votante.
   */
  function initVerifyVisuals() {
    const openButton = $('btn-go-verify');
    const startButton = $('btn-start-verify');
    const cancelButton = $('btn-cancel-verify');

    openButton?.addEventListener('click', () => {
      setStep(0);
      setProgress(18);
    });

    startButton?.addEventListener('click', () => {
      setStep(1);
      setProgress(75);
    });

    cancelButton?.addEventListener('click', () => {
      setStep(0);
      setProgress(0);
      openButton?.classList.remove('is-loading');
      const icon = openButton?.querySelector('i');
      if (icon) icon.className = 'ti ti-fingerprint';
    });
  }
  /**
   * Configura la interacción visual del tarjetón electoral.
   *
   * Resalta visualmente la candidatura seleccionada para
   * minimizar errores de interpretación antes de emitir
   * el voto definitivo.
   */
  function initBallotVisuals() {
    const ballot = $('vote-ballot');
    if (!ballot) return;
    ballot.addEventListener('change', (event) => {
      if (!event.target.matches('input[name="candidato"]')) return;
      document.querySelectorAll('.kiosk-candidate').forEach((card) => {
        card.classList.toggle('selected', card.contains(event.target));
      });
    });
  }
  /**
   * Punto de entrada de inicialización visual.
   *
   * Garantiza que todos los elementos del DOM estén disponibles
   * antes de registrar eventos y aplicar estados iniciales
   * de la interfaz de votación.
   */
  document.addEventListener('DOMContentLoaded', () => {
    setProgress(0);
    initWelcomeFeedback();
    initVerifyVisuals();
    initBallotVisuals();
  });
})();
