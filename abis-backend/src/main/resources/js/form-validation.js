const FormValidator = {
  defaults: {
    debounceMs: 150,
    validateOn: 'blur', // 'blur' | 'input' | 'submit'
    errorClass: 'field-error-msg',
    invalidClass: 'is-invalid',
    validClass: 'is-valid'
  },

  rules: {
    required: {
      validate: (value) => {
        if (value === null || value === undefined) return false;
        if (typeof value === 'boolean') return value === true;
        return String(value).trim().length > 0;
      },
      message: 'Este campo es obligatorio'
    },
    maxLength: {
      validate: (value, max) => String(value ?? '').length <= max,
      message: (max) => `Máximo ${max} caracteres`
    },
    minLength: {
      validate: (value, min) => String(value ?? '').length >= min,
      message: (min) => `Mínimo ${min} caracteres`
    },
    noNumbers: {
      validate: (value) => !/\d/.test(String(value ?? '')),
      message: 'No se permiten números'
    },
    lettersOnly: {
      validate: (value) => /^[a-zA-ZáéíóúÁÉÍÓÚñÑ\s]*$/.test(String(value ?? '')),
      message: 'Solo letras y espacios'
    },
    email: {
      validate: (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value ?? '')),
      message: 'Correo inválido (falta @ o dominio)'
    },
    emailStrict: {
      validate: (value) => /^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$/.test(String(value ?? '')),
      message: 'Correo electrónico no válido'
    },
    cedula: {
      validate: (value) => /^\d{5,12}$/.test(String(value ?? '').replace(/\D/g, '')),
      message: 'Cédula debe tener entre 5 y 12 dígitos'
    },
    phone: {
      validate: (value) => /^\d{7,15}$/.test(String(value ?? '').replace(/\D/g, '')),
      message: 'Teléfono debe tener entre 7 y 15 dígitos'
    },
    password: {
      validate: (value) => String(value ?? '').length >= 4 && String(value ?? '').length <= 25,
      message: 'La contraseña debe tener entre 4 y 25 caracteres'
    },
    passwordStrong: {
      validate: (value) => String(value ?? '').length >= 8 && /[A-Z]/.test(String(value ?? '')) && /[0-9]/.test(String(value ?? '')),
      message: 'Mínimo 8 caracteres, 1 mayúscula y 1 número'
    },
    username: {
      validate: (value) => /^[a-zA-Z0-9_]{3,20}$/.test(String(value ?? '')),
      message: 'Usuario: 3-20 caracteres, solo letras, números y _'
    },
    numCampana: {
      validate: (value) => /^\d{1,4}$/.test(String(value ?? '')) && parseInt(value) >= 1 && parseInt(value) <= 9999,
      message: 'Número de campaña: 1 a 9999'
    },
    positiveNumber: {
      validate: (value) => parseFloat(value) > 0,
      message: 'Debe ser un número mayor a 0'
    },
    fechaInicio: {
      validate: (value) => {
        if (!value) return false;
        return new Date(value) > new Date();
      },
      message: 'La fecha debe ser futura'
    },
    fechaFin: {
      validate: (value, inicio) => {
        if (!value || !inicio) return true;
        return new Date(value) > new Date(inicio);
      },
      message: 'La fecha de fin debe ser posterior al inicio'
    },
    url: {
      validate: (value) => !value || /^https?:\/\/.+/.test(String(value)),
      message: 'URL debe comenzar con http:// o https://'
    },
    alphanumeric: {
      validate: (value) => /^[a-zA-Z0-9áéíóúÁÉÍÓÚñÑ\s]*$/.test(String(value ?? '')),
      message: 'Solo letras, números y espacios'
    },
    noSpecialChars: {
      validate: (value) => !/[<>\"'&]/.test(String(value ?? '')),
      message: 'Caracteres especiales no permitidos'
    },
    ci: {
      validate: (value) => /^\d{5,12}[A-Z]?$/.test(String(value ?? '').toUpperCase()),
      message: 'Identificación no válida'
    }
  },

  setupForm(form, config = {}) {
    const options = { ...this.defaults, ...config };
    const fields = form.querySelectorAll('[data-validate]');

    fields.forEach((field) => {
      const rules = field.dataset.validate.split('|');
      const wrapper = field.closest('.field') || field.closest('label') || field.parentElement;
      let errorEl = wrapper?.querySelector(`.${options.errorClass}`);

      if (!errorEl && wrapper) {
        errorEl = document.createElement('small');
        errorEl.className = options.errorClass;
        errorEl.style.cssText = 'display:block;font-size:0.72rem;color:#dc2626;font-weight:600;margin-top:4px;min-height:18px;';
        wrapper.appendChild(errorEl);
      }

      const validate = () => {
        let firstFailedRule = null;
        let value = field.type === 'checkbox' ? field.checked : field.value;

        for (const rule of rules) {
          const [ruleName, param] = rule.split(':');
          const validator = this.rules[ruleName];
          if (!validator) continue;

          let valid = validator.validate(value, param);
          if (ruleName === 'fechaFin' && param === 'inicio') {
            valid = validator.validate(value, document.getElementById(param)?.value);
          }
          if (ruleName === 'required' && field.type === 'checkbox') {
            valid = validator.validate(field.checked);
          }

          if (!valid) {
            firstFailedRule = validator;
            break;
          }
        }

        if (firstFailedRule) {
          const msg = typeof firstFailedRule.message === 'function'
            ? firstFailedRule.message(param)
            : firstFailedRule.message;
          if (errorEl) errorEl.textContent = msg;
          field.classList.remove(options.validClass);
          field.classList.add(options.invalidClass);
          return false;
        } else {
          if (errorEl) errorEl.textContent = '';
          field.classList.remove(options.invalidClass);
          if (value) field.classList.add(options.validClass);
          return true;
        }
      };

      if (options.validateOn === 'input') {
        field.addEventListener('input', () => {
          if (field.classList.contains(options.invalidClass) || field.value.length > 2) {
            debounce(validate, options.debounceMs)();
          }
        });
      }

      field.addEventListener('blur', validate);
      field.addEventListener('change', validate);

      if (field.type === 'checkbox') {
        field.addEventListener('change', validate);
      }
    });

    form.addEventListener('submit', (e) => {
      e.preventDefault();
      let allValid = true;
      fields.forEach((field) => {
        const rules = field.dataset.validate.split('|');
        let valid = true;
        for (const rule of rules) {
          const [ruleName, param] = rule.split(':');
          const validator = this.rules[ruleName];
          if (!validator) continue;
          let value = field.type === 'checkbox' ? field.checked : field.value;
          let check = validator.validate(value, param);
          if (ruleName === 'required' && field.type === 'checkbox') check = validator.validate(field.checked);
          if (ruleName === 'fechaFin' && param === 'inicio') {
            check = validator.validate(value, document.getElementById(param)?.value);
          }
          if (!check) { valid = false; break; }
        }
        if (!valid) {
          allValid = false;
          const wrapper = field.closest('.field') || field.closest('label') || field.parentElement;
          const errorEl = wrapper?.querySelector(`.${options.errorClass}`);
          if (errorEl && rules[0]) {
            const [ruleName, param] = rules[0].split(':');
            const v = this.rules[ruleName];
            if (v) errorEl.textContent = typeof v.message === 'function' ? v.message(param) : v.message;
          }
          field.classList.add(options.invalidClass);
        }
      });

      if (!allValid) {
        const firstInvalid = form.querySelector(`.${options.invalidClass}`);
        if (firstInvalid) firstInvalid.focus();
        return false;
      }

      if (typeof config.onSubmit === 'function') {
        return config.onSubmit(e);
      }
      return true;
    });

    return this;
  }
};

function debounce(fn, delay) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}

function validarCampo(campo, ...rules) {
  for (const rule of rules) {
    const [ruleName, param] = rule.split(':');
    const v = FormValidator.rules[ruleName];
    if (!v) continue;
    let value = campo.type === 'checkbox' ? campo.checked : campo.value;
    let valid = v.validate(value, param);
    if (!valid) return { valid: false, message: typeof v.message === 'function' ? v.message(param) : v.message };
  }
  return { valid: true };
}

window.FormValidator = FormValidator;
window.validarCampo = validarCampo;
window.debounce = debounce;