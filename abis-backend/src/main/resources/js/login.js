const form = document.getElementById('login-form');
        const btnSubmit = document.getElementById('btn-submit');
        const passwordInput = document.getElementById('password');
        const togglePassword = document.getElementById('toggle-password');
        const forgotLink = document.getElementById('forgot-link');
        const recoverSection = document.getElementById('recover-section');
        const btnRecover = document.getElementById('btn-recover');
        const btnBackLogin = document.getElementById('btn-back-login');
        const recoverMsg = document.getElementById('recover-msg');
        const recoverUsuario = document.getElementById('recover-usuario');

        togglePassword.addEventListener('click', function () {
            var visible = passwordInput.type === 'text';
            passwordInput.type = visible ? 'password' : 'text';
            togglePassword.setAttribute('aria-label', visible ? 'Mostrar contrasena' : 'Ocultar contrasena');
            togglePassword.querySelector('.material-symbols-outlined').textContent = visible ? 'visibility' : 'visibility_off';
        });

        forgotLink.addEventListener('click', function (e) {
            e.preventDefault();
            form.querySelectorAll(':scope > .form-group, :scope > .form-meta, :scope > #btn-submit').forEach(function (el) { el.style.display = 'none'; });
            recoverSection.style.display = 'block';
            recoverMsg.style.display = 'none';
        });

        btnBackLogin.addEventListener('click', function () {
            recoverSection.style.display = 'none';
            form.querySelectorAll(':scope > .form-group, :scope > .form-meta, :scope > #btn-submit').forEach(function (el) { el.style.display = ''; });
        });

        btnRecover.addEventListener('click', async function () {
            var usuario = recoverUsuario.value.trim();
            if (!usuario) {
                window.showToast('Ingrese su usuario', 'error');
                return;
            }
            btnRecover.disabled = true;
            btnRecover.textContent = 'Enviando...';
            recoverMsg.style.display = 'none';

            try {
                var response = await fetch('http://localhost:7000/api/auth/recuperar', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ usuario: usuario })
                });
                var data = await response.json();
                recoverMsg.textContent = 'Solicitud enviada. El administrador del sistema se contactara al correo registrado.';
                recoverMsg.className = 'recover-msg recover-success';
                recoverMsg.style.display = 'block';
            } catch (error) {
                recoverMsg.textContent = 'Error de conexion con el servidor';
                recoverMsg.className = 'recover-msg recover-error';
                recoverMsg.style.display = 'block';
            } finally {
                btnRecover.disabled = false;
                btnRecover.textContent = 'Enviar solicitud';
            }
        });

        form.addEventListener('submit', async function (event) {
            event.preventDefault();

            var usuario = document.getElementById('usuario').value.trim();
            var password = passwordInput.value;

            if (!usuario) { window.showToast('Ingrese su usuario', 'error'); return; }
            if (password.length > 25) { window.showToast('La contrasena no puede exceder 25 caracteres', 'error'); return; }
            if (!password) { window.showToast('Ingrese su contrasena', 'error'); return; }

            btnSubmit.disabled = true;
            btnSubmit.textContent = 'Verificando...';

            try {
                var response = await fetch('http://localhost:7000/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ usuario: usuario, password: password })
                });
                var data = await response.json();

                if (response.ok && data.success) {
                    localStorage.setItem('abis_token', data.token || 'authenticated');
                    localStorage.setItem('abis_user', JSON.stringify(data.user || { usuario: usuario }));
                    window.location.replace('/pages/admin/index.html');
                    return;
                }

                window.showToast(data.message || data.error || 'Credenciales incorrectas', 'error');
            } catch (error) {
                window.showToast('Error de conexion con el servidor', 'error');
            } finally {
                btnSubmit.disabled = false;
                btnSubmit.textContent = 'Ingresar';
            }
        });
