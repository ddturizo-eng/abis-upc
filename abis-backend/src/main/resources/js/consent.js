/**
 * Paso de consentimiento informado del proceso de registro.
 *
 * Garantiza que el ciudadano tenga acceso al texto completo de
 * autorización para tratamiento de datos personales antes de
 * continuar con el enrolamiento biométrico. El flujo exige una
 * acción explícita de aceptación para cumplir los principios de
 * consentimiento informado establecidos por la Ley 1581 de 2012.
 */

const ConsentStep = {

  /**
   * Inicializa los controles de aceptación del consentimiento.
   *
   * Habilita progresivamente la confirmación únicamente cuando el
   * usuario ha recorrido completamente el documento legal y ha
   * manifestado su aceptación mediante la casilla correspondiente.
   * Una vez aceptado, almacena la evidencia de consentimiento para
   * su uso durante el proceso de registro electoral.
   */

  init() {
    const scrollBox = document.getElementById('consent-scroll');
    const checkbox = document.getElementById('consent-checkbox');
    const button = document.getElementById('btn-consent-accept');
    const hint = document.getElementById('consent-hint');
    /**
     * Actualiza el estado de los controles de aceptación.
     *
     * Verifica que el usuario haya visualizado la totalidad del texto
     * legal antes de permitir la activación de la casilla de aceptación
     * y del botón para continuar al siguiente paso del proceso.
     */
    const update = () => {
      const hasRead = scrollBox.scrollTop + scrollBox.clientHeight >= scrollBox.scrollHeight - 6;
      checkbox.disabled = !hasRead;
      if (hasRead) hint.textContent = 'Marque la casilla para continuar.';
      const enabled = hasRead && checkbox.checked;
      button.disabled = !enabled;
      button.classList.toggle('opacity-50', !enabled);
      button.classList.toggle('cursor-not-allowed', !enabled);
    };

    scrollBox.addEventListener('scroll', update);
    checkbox.addEventListener('change', update);

    // Se registra la aceptación junto con fecha y fundamento legal
// para mantener evidencia verificable del consentimiento otorgado.

    button.addEventListener('click', async () => {
      VotanteSession.setConsentimiento({
        accepted: true,
        acceptedAt: new Date().toISOString(),
        legalBase: 'Ley 1581 de 2012; Decreto 1377 de 2013'
      });
      await Router.irA(1);
    });
    update();
  }
};

/**
 * Expone el módulo globalmente para integración con el router
 * del proceso de registro y carga dinámica de vistas.
 */

window.ConsentStep = ConsentStep;
