(function () {
  const STACK_ID = 'abis-toast-stack';
  const DURATION = 4000;

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

  function iconFor(type) {
    const icons = { success: 'check_circle', error: 'error', warning: 'warning', info: 'info' };
    return `<span class="material-symbols-outlined toast-icon">${icons[type] || icons.info}</span>`;
  }

  function createToast(message, type) {
    const toast = document.createElement('div');
    toast.className = `toast-item toast-${type}`;
    toast.innerHTML = `${iconFor(type)}<span class="toast-msg">${escapeHtml(message)}</span><button class="toast-close" aria-label="Cerrar">&times;</button>`;
    return toast;
  }

  function removeToast(toast) {
    toast.classList.add('toast-exit');
    setTimeout(() => { if (toast.parentNode) toast.remove(); }, 300);
  }

  window.showToast = function (message, type = 'info') {
    const stack = ensureStack();
    const toast = createToast(message, type);
    toast.querySelector('.toast-close').addEventListener('click', () => removeToast(toast));
    stack.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add('toast-enter'));
    setTimeout(() => { if (toast.parentNode) removeToast(toast); }, DURATION);
  };

  // Compatibilidad: exponer como mostrarNotificacion para modulos existentes
  window.mostrarNotificacion = window.showToast;

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[c]));
  }
})();
