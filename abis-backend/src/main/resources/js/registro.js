/**
 * Valida la existencia de una sesión administrativa activa.
 *
 * El flujo de registro solo puede ser ejecutado desde un contexto
 * autenticado. Si no existe un token válido, se redirige al usuario
 * al módulo de autenticación para evitar accesos no autorizados.
 */
const token = localStorage.getItem('abis_token');
        if (!token) {
            window.location.replace('/pages/auth/login.html');
        }
/**
 * Verifica el estado operativo de los servicios backend.
 *
 * Consulta el endpoint de salud del sistema y actualiza el indicador
 * visual mostrado en la interfaz. Esta validación permite detectar
 * tempranamente problemas de conectividad antes de iniciar procesos
 * de OCR, biometría o persistencia de información.
 *
 * @returns {Promise<void>}
 */
        async function checkHealth() {
            const indicator = document.getElementById('status-indicator');
            try {
                const result = await ApiHealth.check();
                if (!indicator) return;
                if (result.success) {
                    indicator.innerHTML = '<span class="inline-block w-2 h-2 rounded-full bg-green-500 mr-2"></span>Backend conectado';
                    indicator.className = 'text-xs text-green-600';
                } else {
                    indicator.innerHTML = '<span class="inline-block w-2 h-2 rounded-full bg-red-500 mr-2"></span>Backend desconectado';
                    indicator.className = 'text-xs text-red-600';
                }
            } catch (error) {
                if (!indicator) return;
                indicator.innerHTML = '<span class="inline-block w-2 h-2 rounded-full bg-red-500 mr-2"></span>Error de conexi&oacute;n';
                indicator.className = 'text-xs text-red-600';
            }
        }
/**
 * Punto de entrada principal del módulo de registro.
 *
 * Una vez cargado el DOM:
 *   1. Verifica la disponibilidad de los servicios backend.
 *   2. Recupera el estado del consentimiento informado.
 *   3. Restaura el paso correcto del asistente de registro.
 *
 * Este comportamiento permite reanudar el flujo cuando el usuario
 * regresa a la página sin perder el progreso almacenado en sesión.
 */
        window.addEventListener('DOMContentLoaded', async () => {
            await checkHealth();
            await Router.irA(VotanteSession.getConsentimiento() ? 1 : 0);
        });
