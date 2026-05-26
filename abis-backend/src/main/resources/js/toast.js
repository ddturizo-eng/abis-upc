(function () {
  const STACK_ID = 'abis-toast-stack';
  const DURATION_SUCCESS = 5000;
  const DURATION_WARNING = 5000;
  const DURATION_INFO = 5000;

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
    setTimeout(function () { if (toast.parentNode) toast.remove(); }, 300);
  }

  window.showToast = function (message, type) {
    type = type || 'info';
    var stack = ensureStack();
    var toast = createToast(message, type);
    toast.querySelector('.toast-close').addEventListener('click', function () { removeToast(toast); });
    stack.appendChild(toast);
    requestAnimationFrame(function () { toast.classList.add('toast-enter'); });
    if (type !== 'error') {
      var duration = type === 'success' ? DURATION_SUCCESS : type === 'warning' ? DURATION_WARNING : DURATION_INFO;
      setTimeout(function () { if (toast.parentNode) removeToast(toast); }, duration);
    }
  };

  window.mostrarNotificacion = window.showToast;

  function escapeHtml(value) {
    return String(value || '').replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[c];
    });
  }
})();
