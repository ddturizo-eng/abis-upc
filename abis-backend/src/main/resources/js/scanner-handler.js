const ScannerHandler = {
  maxLength: 500,

  normalize(value) {
    return String(value || '')
      .replace(/\r\n/g, '\n')
      .replace(/\r/g, '\n')
      .replace(/[\u200B-\u200D\uFEFF]/g, '')
      .replace(/[^\S\n]+/g, ' ')
      .trim()
      .slice(0, ScannerHandler.maxLength);
  },

  bindInput(input, options = {}) {
    if (!input) return null;

    const onScan = typeof options.onScan === 'function' ? options.onScan : () => {};
    const onChange = typeof options.onChange === 'function' ? options.onChange : () => {};

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

window.ScannerHandler = ScannerHandler;
