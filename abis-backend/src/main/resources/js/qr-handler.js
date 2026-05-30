/**
 * Gestiona la captura y procesamiento del código QR asociado
 * a la identificación del votante.
 *
 * Centraliza la interacción con lectores QR, la sincronización
 * de datos entre pasos del registro y la generación de códigos
 * QR descargables mediante el microservicio biométrico.
 */
const QrHandler = {
  /**
   * Último valor capturado o restaurado para el QR_CEDULA.
   *
   * Permite conservar el estado durante la navegación entre
   * componentes sin depender exclusivamente del contenido
   * del campo visual.
   */
  value: '',
  /**
   * Inicializa la captura del QR durante el prerregistro.
   *
   * Configura la integración con lectores tipo teclado,
   * actualiza indicadores visuales de estado y notifica
   * cambios mediante eventos personalizados para mantener
   * desacoplados los distintos componentes del flujo.
   */
  initPreRegistro() {
    const field = document.getElementById('f-qr-cedula');
    const status = document.getElementById('qr-status');
    const focusButton = document.getElementById('btn-qr-focus');

    if (!field) return;
    /**
     * Actualiza el valor capturado y sincroniza la interfaz.
     *
     * Normaliza el contenido recibido, actualiza indicadores
     * visuales y publica eventos para informar cambios a otros
     * módulos interesados en el QR capturado.
     *
     * @param {string} value Valor leído por el escáner.
     * @param {boolean} complete Indica si la lectura fue completada.
     */
    const setValue = (value, complete = false) => {
      QrHandler.value = ScannerHandler.normalize(value);
      field.value = QrHandler.value;
      field.classList.toggle('filled', !!QrHandler.value);
      if (status) {
        status.textContent = QrHandler.value
          ? (complete ? 'Lectura del reverso capturada' : 'Lectura detectada')
          : 'QR_CEDULA pendiente';
      }
      document.dispatchEvent(new CustomEvent('abis:qr-cedula-change', { detail: QrHandler.value }));
    };

    ScannerHandler.bindInput(field, {
      onScan: (value) => setValue(value, true),
      onChange: (value) => setValue(value, false)
    });

    if (focusButton) {
      focusButton.addEventListener('click', () => field.focus());
    }

    setValue(field.value || QrHandler.value, false);
  },
  /**
   * Restaura un valor previamente capturado en la interfaz.
   *
   * Se utiliza cuando el usuario regresa a un paso anterior
   * del asistente y es necesario reconstruir el estado visual
   * sin requerir una nueva lectura del documento.
   *
   * @param {string} value Valor de QR previamente almacenado.
   */
  hydrate(value) {
    QrHandler.value = ScannerHandler.normalize(value);
    const field = document.getElementById('f-qr-cedula');
    if (field && QrHandler.value) {
      field.value = QrHandler.value;
      field.classList.add('filled');
    }
  },
  /**
   * Restaura un valor previamente capturado en la interfaz.
   *
   * Se utiliza cuando el usuario regresa a un paso anterior
   * del asistente y es necesario reconstruir el estado visual
   * sin requerir una nueva lectura del documento.
   *
   * @param {string} value Valor de QR previamente almacenado.
   */
  renderSummary(value) {
    const summary = document.getElementById('summary-qr');
    const button = document.getElementById('btn-download-qr');
    if (!summary || !button) return;
    const qrValue = ScannerHandler.normalize(value);
    summary.textContent = qrValue || 'No se registro QR_CEDULA.';
    button.disabled = !qrValue;
    button.classList.toggle('opacity-50', !qrValue);
    button.addEventListener('click', () => QrHandler.downloadQr(qrValue));
  },
  /**
   * Solicita la generación de una imagen QR descargable.
   *
   * Delega la construcción gráfica del código al servicio
   * biométrico para garantizar un formato uniforme entre
   * los distintos módulos del sistema.
   *
   * @param {string} value Información que será codificada.
   * @returns {Promise<void>}
   */
  async downloadQr(value) {
    const response = await fetch('http://localhost:8001/qr/render', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ value })
    });
    if (!response.ok) return;
    const blob = await response.blob();
    const link = document.createElement('a');
    link.download = 'qr-cedula-abis-upc.png';
    link.href = URL.createObjectURL(blob);
    link.click();
    URL.revokeObjectURL(link.href);
  }
};
/**
 * Expone el gestor de QR para reutilización en los módulos
 * de registro, validación y contingencia.
 */
window.QrHandler = QrHandler;
