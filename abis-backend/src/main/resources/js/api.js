/**
 * ABIS-UPC | API Connection Module
 * Maneja todas las comunicaciones con el backend Java
 */

const API = {
    baseUrl: '',

    async request(endpoint, options = {}) {
        const url = this.baseUrl + endpoint;
        const config = {
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            },
            ...options
        };

        if (config.body && typeof config.body === 'object' && !(config.body instanceof FormData)) {
            config.body = JSON.stringify(config.body);
        }

        if (config.body instanceof FormData) {
            delete config.headers['Content-Type'];
        }

        try {
            const response = await fetch(url, config);
            const data = await response.json();
            
            if (!response.ok) {
                throw new Error(data.error || data.message || `HTTP ${response.status}`);
            }
            
            return data;
        } catch (error) {
            console.error(`[API] Error en ${endpoint}:`, error);
            throw error;
        }
    },

    async get(endpoint) {
        return this.request(endpoint, { method: 'GET' });
    },

    async post(endpoint, body) {
        return this.request(endpoint, { method: 'POST', body });
    },

    async postForm(endpoint, formData) {
        return this.request(endpoint, { 
            method: 'POST', 
            body: formData,
            headers: {}
        });
    }
};

const ApiHealth = {
    async check() {
        try {
            const data = await API.get('/api/health');
            return {
                success: data.biometric === 'ok',
                java: true,
                python: data.biometric === 'ok',
                message: data.message || 'Sistema operativo'
            };
        } catch (error) {
            return {
                success: false,
                java: false,
                python: false,
                message: error.message
            };
        }
    },

    async status() {
        try {
            return await API.get('/api/status');
        } catch (error) {
            return { error: error.message };
        }
    }
};

const ApiDocument = {
    async scan(archivoFront, archivoBack = null, docType = 'auto') {
        const formData = new FormData();
        formData.append('front', archivoFront);
        if (archivoBack) formData.append('back', archivoBack);
        formData.append('doc_type', docType);

        const response = await fetch('http://localhost:7000/api/document/scan', {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || errorData.message || `HTTP ${response.status}`);
        }

        return await response.json();
    }
};

const ApiEnrollment = {
    async enroll(identificacion, reEnroll = false) {
        return await API.post('/api/enroll', {
            identificacion,
            re_enroll: reEnroll
        });
    }
};

const ApiVerification = {
    async verify(identificacion) {
        return await API.post('/api/verify', {
            identificacion
        });
    }
};

const ApiAuth = {
    async login(usuario, password) {
        return await API.post('/api/auth/login', {
            usuario,
            password
        });
    },

    async logout() {
        return await API.post('/api/auth/logout', {});
    }
};

const ApiPreRegistro = {
    async crear(data) {
        const payload = {
            identificacion: data.identificacion,
            primer_nombre: data.primerNombre,
            segundo_nombre: data.segundoNombre || '',
            primer_apellido: data.primerApellido,
            segundo_apellido: data.segundoApellido || '',
            correo: data.correo,
            id_rol: parseInt(data.idRol),
            id_puesto: parseInt(data.idPuesto)
        };
        return await API.post('/api/registro/preregistro', payload);
    }
};

window.API = API;
window.ApiHealth = ApiHealth;
window.ApiDocument = ApiDocument;
window.ApiEnrollment = ApiEnrollment;
window.ApiVerification = ApiVerification;
window.ApiAuth = ApiAuth;
window.ApiPreRegistro = ApiPreRegistro;