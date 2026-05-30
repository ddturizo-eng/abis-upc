/**
 * Inicializa las referencias principales del formulario de autenticación.
 *
 * El acceso al panel administrativo requiere credenciales válidas
 * emitidas por el backend. Todos los elementos se obtienen una sola
 * vez para evitar búsquedas repetidas en el DOM durante el proceso
 * de autenticación.
 */
const form = document.getElementById('login-form');
        const errorMsg = document.getElementById('error-msg');
        const btnSubmit = document.getElementById('btn-submit');
        const passwordInput = document.getElementById('password');
        const togglePassword = document.getElementById('toggle-password');
/**
 * Permite alternar la visibilidad de la contraseña ingresada.
 *
 * Esta funcionalidad mejora la usabilidad durante la autenticación
 * reduciendo errores de digitación sin comprometer el mecanismo de
 * validación implementado por el backend.
 */
        togglePassword.addEventListener('click', () => {
            const visible = passwordInput.type === 'text';
            passwordInput.type = visible ? 'password' : 'text';
            togglePassword.setAttribute('aria-label', visible ? 'Mostrar contraseña' : 'Ocultar contraseña');
            togglePassword.querySelector('.material-symbols-outlined').textContent = visible ? 'visibility' : 'visibility_off';
        });
/**
 * Gestiona el proceso de autenticación administrativa.
 *
 * Realiza validaciones básicas en cliente antes de enviar las
 * credenciales al backend, reduciendo solicitudes inválidas y
 * proporcionando retroalimentación inmediata al usuario.
 *
 * Flujo:
 *   1. Valida usuario y contraseña.
 *   2. Envía credenciales al servicio de autenticación.
 *   3. Almacena el token de sesión recibido.
 *   4. Redirige al panel administrativo.
 *   5. Muestra mensajes de error cuando la autenticación falla.
 *
 * @param {SubmitEvent} event Evento de envío del formulario.
 */
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            errorMsg.classList.remove('show');

            const usuario = document.getElementById('usuario').value.trim();
            const password = passwordInput.value;

            if (!usuario) { errorMsg.textContent = 'Ingrese su usuario'; errorMsg.classList.add('show'); return; }
            if (password.length > 25) { errorMsg.textContent = 'La contraseña no puede exceder 25 caracteres'; errorMsg.classList.add('show'); return; }
            if (!password) { errorMsg.textContent = 'Ingrese su contraseña'; errorMsg.classList.add('show'); return; }
// Se bloquea el botón para evitar múltiples intentos concurrentes
// mientras el servidor procesa la autenticación.
            btnSubmit.disabled = true;
            btnSubmit.textContent = 'Verificando...';

            try {
                const response = await fetch('http://localhost:7000/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ usuario, password })
                });
                const data = await response.json();

                if (response.ok && data.success) {
                    // El token se conserva para autenticar futuras solicitudes
                   // protegidas realizadas desde el panel administrativo.
                    localStorage.setItem('abis_token', data.token || 'authenticated');
                    localStorage.setItem('abis_user', JSON.stringify(data.user || { usuario }));
                    // Se utiliza replace() para impedir que el usuario vuelva al
                   // formulario de login mediante el historial del navegador.
                    window.location.replace('/pages/admin/index.html');
                    return;
                }
                errorMsg.textContent = data.message || data.error || 'Credenciales incorrectas';
                errorMsg.classList.add('show');
                // Los errores de conectividad se manejan por separado de los
               // errores de credenciales para facilitar el diagnóstico operativo.
            } catch (error) {
                errorMsg.textContent = 'Error de conexión con el servidor';
                errorMsg.classList.add('show');
            } finally {
                btnSubmit.disabled = false;
                btnSubmit.textContent = 'Ingresar';
            }
        });

