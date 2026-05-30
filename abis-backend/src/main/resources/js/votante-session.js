/**
 * Administra el estado temporal del proceso de registro electoral.
 *
 * Centraliza la persistencia de información entre pasos utilizando
 * sessionStorage para conservar los datos únicamente durante la
 * sesión activa del navegador. Esto evita almacenar información
 * sensible de forma permanente en el dispositivo del usuario.
 */

const VotanteSession = {
  /**
   * Almacena la información capturada durante el registro.
   *
   * Los datos se conservan entre pasos del asistente para evitar
   * pérdidas de información durante la navegación del proceso.
   *
   * @param {Object} data Información de registro del votante.
   */
  setDatosRegistro(data) {
    sessionStorage.setItem('abis_registro', JSON.stringify(data));
  },
  /**
   * Recupera los datos de registro almacenados en la sesión actual.
   *
   * @returns {Object|null} Información registrada o null si no existe.
   */
  getDatosRegistro() {
    return JSON.parse(sessionStorage.getItem('abis_registro') || 'null');
  },
  /**
   * Guarda la identificación del votante durante el flujo de registro.
   *
   * @param {string} id Número de identificación del ciudadano.
   */
  setIdentificacion(id) {
    sessionStorage.setItem('abis_identificacion', id);
  },
  /**
   * Obtiene la identificación almacenada para la sesión actual.
   *
   * @returns {string|null} Identificación registrada o null.
   */
  getIdentificacion() {
    return sessionStorage.getItem('abis_identificacion');
  },
  /**
   * Registra la evidencia de aceptación del consentimiento informado.
   *
   * Conserva la aceptación del tratamiento de datos personales y la
   * información legal asociada durante el proceso de enrolamiento.
   *
   * @param {Object} data Información de consentimiento.
   */
  setConsentimiento(data) {
    sessionStorage.setItem('abis_consentimiento', JSON.stringify(data));
  },
  /**
   * Recupera la información de consentimiento almacenada.
   *
   * @returns {Object|null} Evidencia de consentimiento o null.
   */
  getConsentimiento() {
    return JSON.parse(sessionStorage.getItem('abis_consentimiento') || 'null');
  },
  /**
   * Elimina toda la información temporal de registro.
   *
   * Se utiliza al finalizar o cancelar el proceso para evitar que
   * datos sensibles permanezcan disponibles en sesiones posteriores.
   */
  clear() {
    ['abis_registro', 'abis_identificacion', 'abis_consentimiento'].forEach((key) => sessionStorage.removeItem(key));
  }
};
/**
 * Expone el gestor de sesión para su reutilización en los
 * diferentes pasos del flujo de registro.
 */
window.VotanteSession = VotanteSession;
