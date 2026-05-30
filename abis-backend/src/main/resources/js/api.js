/**
 * Cliente HTTP centralizado de ABIS-UPC.
 *
 * Encapsula toda la comunicación con los servicios backend,
 * gestionando autenticación, serialización de datos, manejo
 * uniforme de errores y soporte para cargas multipart.
 */

const API = {
    baseUrl: '',

    /**
     * Cliente HTTP centralizado de ABIS-UPC.
     *
     * Encapsula toda la comunicación con los servicios backend,
     * gestionando autenticación, serialización de datos, manejo
     * uniforme de errores y soporte para cargas multipart.
     */

    async request(endpoint, options = {}) {
        const url = this.baseUrl + endpoint;
        const token = localStorage.getItem('abis_token');
        const providedHeaders = options.headers || {};
        const isFormData = this.isFormData(options.body);
        const config = {
            ...options,
            headers: {
                ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
                ...(token ? { Authorization: 'Bearer ' + token } : {}),
                ...providedHeaders
            }
        };

        if (config.body && typeof config.body === 'object' && !isFormData) {
            config.body = JSON.stringify(config.body);
        }

        if (isFormData) {
            delete config.headers['Content-Type'];
            delete config.headers['content-type'];
        }

        try {
            const response = await fetch(url, config);
            const text = await response.text();
            let data = {};
            try {
                data = text ? JSON.parse(text) : {};
            } catch (parseError) {
                data = { error: text || `HTTP ${response.status}` };
            }
            
            if (!response.ok) {
                throw new Error(data.error || data.detail || data.message || `HTTP ${response.status}`);
            }
            
            return data;
        } catch (error) {
            console.error(`[API] Error en ${endpoint}:`, error);
            throw error;
        }
    },

    /**
     * Realiza una solicitud GET.
     *
     * @param {string} endpoint Ruta del recurso solicitado.
     * @returns {Promise<Object>}
     */

    async get(endpoint) {
        return this.request(endpoint, { method: 'GET' });
    },

    /**
     * Realiza una solicitud POST con contenido JSON.
     *
     * @param {string} endpoint Ruta del endpoint.
     * @param {*} body Información enviada al servidor.
     * @returns {Promise<Object>}
     */

    async post(endpoint, body) {
        return this.request(endpoint, { method: 'POST', body });
    },

    /**
     * Envía información multipart/form-data.
     *
     * Utilizado principalmente para carga de imágenes,
     * documentos y archivos biométricos.
     *
     * @param {string} endpoint Ruta del endpoint.
     * @param {FormData} formData Datos a transmitir.
     * @returns {Promise<Object>}
     */

    async postForm(endpoint, formData) {
        return this.request(endpoint, { 
            method: 'POST', 
            body: formData,
            headers: {}
        });
    },

    /**
     * Determina si un objeto corresponde a una instancia FormData.
     *
     * Permite preservar correctamente los encabezados multipart
     * requeridos por el navegador durante la carga de archivos.
     *
     * @param {*} body Objeto a validar.
     * @returns {boolean}
     */

    isFormData(body) {
        return typeof FormData !== 'undefined' &&
            (body instanceof FormData || Object.prototype.toString.call(body) === '[object FormData]');
    }
};

/**
 * Servicios de monitoreo y estado operacional.
 *
 * Proporciona verificaciones rápidas de disponibilidad entre
 * el frontend, backend Java y componentes biométricos.
 */

const ApiHealth = {

    /**
     * Servicios de monitoreo y estado operacional.
     *
     * Proporciona verificaciones rápidas de disponibilidad entre
     * el frontend, backend Java y componentes biométricos.
     */

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

    /**
     * Obtiene el estado detallado de los servicios registrados.
     *
     * @returns {Promise<Object>}
     */

    async status() {
        try {
            return await API.get('/api/status');
        } catch (error) {
            return { error: error.message };
        }
    }
};

const ApiDocument = {

    /**
     * Envía imágenes de un documento al motor OCR.
     *
     * Permite procesar documentos de identidad utilizando
     * reconocimiento óptico de caracteres y validaciones
     * automáticas de formato.
     *
     * @param {File} archivoFront Imagen frontal.
     * @param {File|null} archivoBack Imagen posterior.
     * @param {string} docType Tipo de documento.
     * @returns {Promise<Object>}
     */

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

/**
 * Operaciones de enrolamiento biométrico.
 *
 * Gestiona la captura y registro inicial de huellas
 * dactilares dentro del sistema electoral.
 */

const ApiEnrollment = {

    /**
     * Inicia el proceso de enrolamiento biométrico.
     *
     * @param {string} identificacion Identificación del votante.
     * @param {boolean} reEnroll Indica si se trata de un re-enrolamiento.
     * @returns {Promise<Object>}
     */

    async enroll(identificacion, reEnroll = false) {
        return await API.post('/api/enroll', {
            identificacion,
            re_enroll: reEnroll
        });
    },

    /**
     * Consulta el estado actual del proceso de enrolamiento.
     *
     * @returns {Promise<Object>}
     */

    async progress() {
        return await API.get('/api/enroll/progress');
    }
};

/**
 * Servicios de verificación de identidad.
 *
 * Agrupa las operaciones utilizadas durante el proceso
 * de autenticación biométrica y validación electoral.
 */

const ApiVerification = {

    /**
     * Verifica la identidad de un votante mediante biometría.
     *
     * @param {string} identificacion Identificación del votante.
     * @returns {Promise<Object>}
     */

    async verify(identificacion) {
        return await API.post('/api/verify', {
            identificacion
        });
    },

    /**
     * Ejecuta la validación de segunda llave de seguridad.
     *
     * Utilizada como mecanismo adicional de verificación
     * previo al ejercicio del derecho al voto.
     *
     * @param {string} qrCedula Código QR asociado al documento.
     * @param {string} identificacion Identificación del votante.
     * @returns {Promise<Object>}
     */

    async segundaLlave(qrCedula, identificacion) {
        return await API.post('/api/votantes/segunda-llave', {
            qr_cedula: ScannerHandler.normalize(qrCedula),
            identificacion
        });
    }
};

/**
 * Servicios de autenticación administrativa.
 *
 * Gestiona el acceso y cierre de sesión para usuarios
 * autorizados dentro del sistema electoral.
 */

const ApiAuth = {

    /**
     * Inicia sesión de un usuario administrativo.
     *
     * @param {string} usuario Nombre de usuario.
     * @param {string} password Credencial de acceso.
     * @returns {Promise<Object>}
     */

    async login(usuario, password) {
        return await API.post('/api/auth/login', {
            usuario,
            password
        });
    },

    /**
     * Finaliza la sesión autenticada actual.
     *
     * @returns {Promise<Object>}
     */

    async logout() {
        return await API.post('/api/auth/logout', {});
    }
};

/**
 * Servicios de preregistro electoral.
 *
 * Permite registrar información preliminar de un
 * votante antes del proceso de enrolamiento biométrico.
 */

const ApiPreRegistro = {

    /**
     * Registra un nuevo preregistro electoral.
     *
     * Transforma los datos provenientes de la interfaz
     * al formato requerido por los servicios backend.
     *
     * @param {Object} data Información capturada en el formulario.
     * @returns {Promise<Object>}
     */

    async crear(data) {
        const payload = {
            identificacion: data.identificacion,
            primer_nombre: data.primerNombre,
            segundo_nombre: data.segundoNombre || '',
            primer_apellido: data.primerApellido,
            segundo_apellido: data.segundoApellido || '',
            correo: data.correo,
            id_rol: parseInt(data.idRol),
            id_puesto: parseInt(data.idPuesto),
            fecha_nacimiento: data.fechaNacimiento || null
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

/**
 * Cliente de gestión de elecciones.
 *
 * Centraliza las operaciones administrativas relacionadas
 * con jornadas electorales, candidatos y resultados.
 */

const ApiElecciones = {
    listar: () => API.get('/api/elecciones'),
    crear: (data) => API.post('/api/elecciones', data),
    editar: (id, data) => API.request(`/api/elecciones/${id}`, { method: 'PUT', body: data }),
    iniciar: (id) => API.post(`/api/elecciones/${id}/iniciar`, {}),
    cerrar: (id) => API.request(`/api/elecciones/${id}/cerrar`, { method: 'PUT', body: {} }),
    eliminar: (id) => API.request(`/api/elecciones/${id}`, { method: 'DELETE' }),
    candidatos: (id) => API.get(`/api/elecciones/${id}/candidatos`),
    elegibilidad: (id) => API.get(`/api/elecciones/${id}/elegibilidad`),
    resultados: (id) => API.get(`/api/elecciones/${id}/resultados`),
    votantesPorEleccion: (idEleccion) => API.get(`/api/votantes/por-eleccion?idEleccion=${idEleccion}`),
};
/**
 * Servicios de administración de candidatos.
 *
 * Permite registrar, modificar y eliminar candidatos
 * asociados a una elección determinada.
 */
const ApiCandidatos = {
    agregar: (idEleccion, data) =>
        API.post(`/api/elecciones/${idEleccion}/candidatos`, data),
    editar: (idEleccion, idCandidato, data) =>
        API.request(`/api/elecciones/${idEleccion}/candidatos/${idCandidato}`,
            { method: 'PUT', body: data }),
    eliminar: (idEleccion, idCandidato) =>
        API.request(`/api/elecciones/${idEleccion}/candidatos/${idCandidato}`,
            { method: 'DELETE' }),
};
/**
 * Gestión de roles electorales habilitados por elección.
 *
 * Controla los grupos de votantes autorizados para
 * participar en una jornada específica.
 */
const ApiEleccionRoles = {
    listar: (idEleccion) =>
        API.get(`/api/elecciones/${idEleccion}/roles`),
    configurar: (idEleccion, idRol, pesoVoto) =>
        API.post(`/api/elecciones/${idEleccion}/roles`, {
            idRol,
            pesoVoto
        }),
};

window.ApiElecciones = ApiElecciones;
window.ApiCandidatos = ApiCandidatos;
window.ApiEleccionRoles = ApiEleccionRoles;
/**
 * Servicios de certificados de participación.
 *
 * Administra consultas, reenvíos y estadísticas de
 * certificados emitidos a los votantes.
 */
const ApiCertificados = {
    listar: (eleccionId = '', limit = 100) => {
        const params = new URLSearchParams();
        if (eleccionId) params.set('eleccionId', eleccionId);
        if (limit) params.set('limit', limit);
        const query = params.toString();
        return API.get(`/api/certificados${query ? `?${query}` : ''}`);
    },
    resumen: (eleccionId = '') => {
        const params = new URLSearchParams();
        if (eleccionId) params.set('eleccionId', eleccionId);
        const query = params.toString();
        return API.get(`/api/certificados/resumen${query ? `?${query}` : ''}`);
    },
    elecciones: () => API.get('/api/certificados/elecciones'),
    reenviar: (idAuditoria) => API.post(`/api/certificados/${idAuditoria}/reenviar`, {}),
    reenviarVotante: (identificacion, idEleccion) => API.post('/api/certificados/reenviar', { identificacion, idEleccion })
};

window.ApiCertificados = ApiCertificados;
/**
 * Servicios de contingencia electoral.
 *
 * Gestiona la emisión, regeneración, revocación y
 * auditoría de tokens utilizados en escenarios de
 * operación alternativa o recuperación.
 */
const ApiContingencia = {
    resumen: (eleccionId) => API.get(`/api/contingencia/resumen?eleccionId=${encodeURIComponent(eleccionId)}`),
    tokens: (eleccionId, estadoEnvio = '') => {
        const params = new URLSearchParams({ eleccionId });
        if (estadoEnvio) params.set('estadoEnvio', estadoEnvio);
        return API.get(`/api/contingencia/tokens?${params.toString()}`);
    },
    auditoria: (eleccionId = '', limit = 100) => {
        const params = new URLSearchParams();
        if (eleccionId) params.set('eleccionId', eleccionId);
        params.set('limit', limit);
        return API.get(`/api/contingencia/auditoria?${params.toString()}`);
    },
    emitir: (idEleccion) => API.post('/api/contingencia/emisiones', { idEleccion }),
    reenviar: (idToken) => API.post(`/api/contingencia/tokens/${encodeURIComponent(idToken)}/reenviar`, {}),
    revocar: (idToken) => API.post(`/api/contingencia/tokens/${encodeURIComponent(idToken)}/revocar`, {}),
    regenerar: (idToken) => API.post(`/api/contingencia/tokens/${encodeURIComponent(idToken)}/regenerar`, {})
};

window.ApiContingencia = ApiContingencia;
