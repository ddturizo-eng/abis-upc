const token = localStorage.getItem('abis_token');
        if (!token) {
            window.location.replace('/pages/auth/login.html');
        }

        async function checkHealth() {
            try {
                const result = await ApiHealth.check();
                const indicator = document.getElementById('status-indicator');
                if (result.success) {
                    indicator.innerHTML = '<span class="inline-block w-2 h-2 rounded-full bg-green-500 mr-2"></span>Backend conectado';
                    indicator.className = 'text-xs text-green-600';
                } else {
                    indicator.innerHTML = '<span class="inline-block w-2 h-2 rounded-full bg-red-500 mr-2"></span>Backend desconectado';
                    indicator.className = 'text-xs text-red-600';
                }
            } catch (error) {
                const indicator = document.getElementById('status-indicator');
                indicator.innerHTML = '<span class="inline-block w-2 h-2 rounded-full bg-red-500 mr-2"></span>Error de conexi&oacute;n';
                indicator.className = 'text-xs text-red-600';
            }
        }

        window.addEventListener('DOMContentLoaded', async () => {
            await checkHealth();
            await Router.irA(VotanteSession.getConsentimiento() ? 1 : 0);
        });
