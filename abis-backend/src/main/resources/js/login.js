const form = document.getElementById('login-form');
        const errorMsg = document.getElementById('error-msg');
        const btnSubmit = document.getElementById('btn-submit');
        const passwordInput = document.getElementById('password');
        const togglePassword = document.getElementById('toggle-password');

        togglePassword.addEventListener('click', () => {
            const visible = passwordInput.type === 'text';
            passwordInput.type = visible ? 'password' : 'text';
            togglePassword.setAttribute('aria-label', visible ? 'Mostrar contraseña' : 'Ocultar contraseña');
            togglePassword.querySelector('.material-symbols-outlined').textContent = visible ? 'visibility' : 'visibility_off';
        });

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            errorMsg.classList.remove('show');

            const usuario = document.getElementById('usuario').value.trim();
            const password = passwordInput.value;

            if (!usuario) { errorMsg.textContent = 'Ingrese su usuario'; errorMsg.classList.add('show'); return; }
            if (password.length > 25) { errorMsg.textContent = 'La contraseña no puede exceder 25 caracteres'; errorMsg.classList.add('show'); return; }
            if (!password) { errorMsg.textContent = 'Ingrese su contraseña'; errorMsg.classList.add('show'); return; }

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
                    localStorage.setItem('abis_token', data.token || 'authenticated');
                    localStorage.setItem('abis_user', JSON.stringify(data.user || { usuario }));
                    window.location.replace('/pages/admin/index.html');
                    return;
                }

                errorMsg.textContent = data.message || data.error || 'Credenciales incorrectas';
                errorMsg.classList.add('show');
            } catch (error) {
                errorMsg.textContent = 'Error de conexión con el servidor';
                errorMsg.classList.add('show');
            } finally {
                btnSubmit.disabled = false;
                btnSubmit.textContent = 'Ingresar';
            }
        });

