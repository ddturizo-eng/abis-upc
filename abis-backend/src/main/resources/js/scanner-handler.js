/**
 * Gestiona la captura y normalización de datos provenientes de
 * lectores QR, escáneres de documentos y entradas manuales.
 *
 * Centraliza las reglas de limpieza de texto para garantizar que
 * los distintos dispositivos de captura produzcan resultados
 * consistentes antes de ser procesados por los módulos de
 * registro, verificación o contingencia.
 */
const ScannerHandler = {
  /**
   * Longitud máxima permitida para entradas capturadas.
   *
   * Limita el tamaño de los datos procesados para prevenir
   * entradas anómalas generadas por errores de lectura o
   * dispositivos mal configurados.
   */
  maxLength: 500,
  /**
   * Normaliza el contenido capturado por dispositivos de entrada.
   *
   * Elimina caracteres invisibles, homogeniza saltos de línea,
   * reduce espacios redundantes y aplica límites de longitud para
   * garantizar que los datos enviados al backend tengan un formato
   * predecible independientemente del origen de captura.
   *
   * @param {*} value Valor capturado por el dispositivo.
   * @returns {string} Texto normalizado.
   */
  normalize(value) {
    return String(value || '')
      .replace(/\r\n/g, '\n')
      .replace(/\r/g, '\n')
      .replace(/[\u200B-\u200D\uFEFF]/g, '')
      .replace(/[^\S\n]+/g, ' ')
      .trim()
      .slice(0, ScannerHandler.maxLength);
  },
  /**
   * Asocia un campo de entrada con el flujo de captura del sistema.
   *
   * Configura eventos y validaciones necesarias para soportar
   * lectores QR tipo teclado, escáneres de documentos y captura
   * manual, proporcionando una interfaz uniforme para el resto
   * de módulos de la aplicación.
   *
   * @param {HTMLInputElement} input Campo de entrada a gestionar.
   * @param {Object} [options={}] Opciones de configuración.
   * @param {Function} [options.onScan] Callback ejecutado al completar una lectura.
   * @param {Function} [options.onChange] Callback ejecutado ante cambios de contenido.
   * @returns {Object|null} Controlador de interacción o null si el elemento no existe.
   */
  bindInput(input, options = {}) {
    if (!input) return null;

    const onScan = typeof options.onScan === 'function' ? options.onScan : () => {};
    const onChange = typeof options.onChange === 'function' ? options.onChange : () => {};
    /**
     * Finaliza el proceso de captura aplicando normalización y
     * notificando a los consumidores registrados del resultado.
     */
    const complete = () => {
      const normalized = ScannerHandler.normalize(input.value);
      input.value = normalized;
      input.classList.toggle('filled', !!normalized);
      if (normalized) onScan(normalized);
      onChange(normalized);
    };

    input.setAttribute('autocomplete', 'off');
    input.setAttribute('spellcheck', 'false');
    input.maxLength = ScannerHandler.maxLength;

    input.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        complete();
      }
    });

    input.addEventListener('input', () => {
      const normalized = ScannerHandler.normalize(input.value);
      if (input.value.length > ScannerHandler.maxLength) {
        input.value = normalized;
      }
      input.classList.toggle('filled', !!normalized);
      onChange(normalized);
    });

    input.addEventListener('blur', complete);
    /**
     * API pública para controlar programáticamente el campo asociado.
     *
     * Permite reutilizar el mismo mecanismo de captura desde distintos
     * flujos sin exponer detalles internos de implementación.
     */
    return {
      focus: () => input.focus(),
      read: () => ScannerHandler.normalize(input.value),
      clear: () => {
        input.value = '';
        input.classList.remove('filled');
        onChange('');
      }
    };
  }
};
/**
 * Expone el gestor de captura para su utilización en los módulos
 * de registro, autenticación, contingencia y votación.
 */
window.ScannerHandler = ScannerHandler;
