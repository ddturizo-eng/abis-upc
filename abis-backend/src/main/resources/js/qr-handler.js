const QrHandler = {
  value: '',

  initPreRegistro() {
    const field = document.getElementById('f-qr-cedula');
    const status = document.getElementById('qr-status');
    const focusButton = document.getElementById('btn-qr-focus');

    if (!field) return;

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

  hydrate(value) {
    QrHandler.value = ScannerHandler.normalize(value);
    const field = document.getElementById('f-qr-cedula');
    if (field && QrHandler.value) {
      field.value = QrHandler.value;
      field.classList.add('filled');
    }
  },

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

window.QrHandler = QrHandler;
