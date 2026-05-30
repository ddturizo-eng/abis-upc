/**
 * Sistema centralizado de notificaciones tipo toast.
 *
 * Proporciona mensajes temporales y no intrusivos para informar
 * resultados de operaciones en el frontend sin interrumpir el flujo
 * de trabajo del usuario. Mantiene compatibilidad con módulos
 * heredados mediante una interfaz global compartida.
 */
(function () {
  const STACK_ID = 'abis-toast-stack';
  const DURATION = 4000;
  /**
   * Garantiza la existencia de un contenedor único para notificaciones.
   *
   * Centralizar todos los mensajes en un único stack evita la creación
   * de múltiples contenedores duplicados cuando distintos módulos
   * solicitan notificaciones simultáneamente.
   *
   * @returns {HTMLElement} Contenedor principal de notificaciones.
   */
  function ensureStack() {
    let stack = document.getElementById(STACK_ID);
    if (!stack) {
      stack = document.createElement('div');
      stack.id = STACK_ID;
      stack.className = 'toast-stack';
      document.body.appendChild(stack);
    }
    return stack;
  }
  /**
   * Obtiene el icono visual asociado a un tipo de notificación.
   *
   * El uso de iconografía consistente facilita el reconocimiento
   * inmediato del contexto del mensaje sin necesidad de leerlo
   * completamente.
   *
   * @param {string} type Tipo de notificación.
   * @returns {string} Marcado HTML del icono correspondiente.
   */
  function iconFor(type) {
    const icons = { success: 'check_circle', error: 'error', warning: 'warning', info: 'info' };
    return `<span class="material-symbols-outlined toast-icon">${icons[type] || icons.info}</span>`;
  }
  /**
   * Construye un elemento visual de notificación.
   *
   * La generación centralizada garantiza una apariencia uniforme
   * y permite aplicar medidas de seguridad sobre el contenido
   * mostrado al usuario.
   *
   * @param {string} message Mensaje a mostrar.
   * @param {string} type Tipo de notificación.
   * @returns {HTMLElement} Elemento toast listo para insertar.
   */
  function createToast(message, type) {
    const toast = document.createElement('div');
    toast.className = `toast-item toast-${type}`;
    toast.innerHTML = `${iconFor(type)}<span class="toast-msg">${escapeHtml(message)}</span><button class="toast-close" aria-label="Cerrar">&times;</button>`;
    return toast;
  }
  /**
   * Elimina una notificación aplicando primero la animación de salida.
   *
   * La transición previa evita desapariciones abruptas y mejora
   * la percepción de fluidez de la interfaz.
   *
   * @param {HTMLElement} toast Notificación a eliminar.
   */
  function removeToast(toast) {
    toast.classList.add('toast-exit');
    setTimeout(() => { if (toast.parentNode) toast.remove(); }, 300);
  }
  /**
   * Muestra una notificación temporal al usuario.
   *
   * Las notificaciones se eliminan automáticamente tras un tiempo
   * configurable para evitar acumulación visual y reducir la
   * necesidad de interacción manual.
   *
   * @param {string} message Mensaje a mostrar.
   * @param {string} [type='info'] Tipo de notificación.
   */
  window.showToast = function (message, type = 'info') {
    const stack = ensureStack();
    const toast = createToast(message, type);
    toast.querySelector('.toast-close').addEventListener('click', () => removeToast(toast));
    stack.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add('toast-enter'));
    setTimeout(() => { if (toast.parentNode) removeToast(toast); }, DURATION);
  };
  /**
   * Alias de compatibilidad para módulos desarrollados antes de la
   * estandarización del sistema de notificaciones.
   */

  window.mostrarNotificacion = window.showToast;
  /**
   * Escapa caracteres especiales antes de insertarlos en el DOM.
   *
   * Previene la interpretación de contenido HTML proporcionado por
   * usuarios o servicios externos, reduciendo riesgos de inyección
   * de código en la interfaz.
   *
   * @param {*} value Valor a sanitizar.
   * @returns {string} Texto seguro para inserción en HTML.
   */
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[c]));
  }
})();
