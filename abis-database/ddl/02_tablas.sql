-- =============================================================================
-- ABIS-UPC | DDL - Creación de tablas y constraints
-- Universidad Popular del Cesar | Programación III | 2026
-- Autor: Jorge Iván Herrera García
-- Base de datos: Oracle Database XE
-- Descripción: Crea todas las tablas del modelo relacional con sus PK
--              y constraints de dominio (CHECK, NOT NULL, UNIQUE).
--              Ejecutar después de 01_secuencias.sql.
--              Las claves foráneas están en 03_claves_foraneas.sql.
-- =============================================================================


-- =============================================================================
-- 1. ROLES
-- =============================================================================
CREATE TABLE Roles (
    id_rol   NUMBER        NOT NULL,
    nombre   VARCHAR2(50)  NOT NULL
);

ALTER TABLE Roles
    ADD CONSTRAINT pk_roles
    PRIMARY KEY (id_rol);

ALTER TABLE Roles
    ADD CONSTRAINT uq_roles_nombre
    UNIQUE (nombre);


-- =============================================================================
-- 2. PUESTOS_VOTACION
-- =============================================================================
CREATE TABLE Puestos_votacion (
    id_puesto     NUMBER,
    ciudad        VARCHAR2(60)   NOT NULL,
    sede          VARCHAR2(100)  NOT NULL,
    nombre_puesto VARCHAR2(80)   NOT NULL,
    hora_inicio   TIMESTAMP      NOT NULL,
    hora_salida   TIMESTAMP      NOT NULL
);

ALTER TABLE Puestos_votacion
    ADD CONSTRAINT pk_puestos_votacion
    PRIMARY KEY (id_puesto);

ALTER TABLE Puestos_votacion
    ADD CONSTRAINT chk_puestos_votacion_horas
    CHECK (hora_salida > hora_inicio);


-- =============================================================================
-- 3. VOTANTES
-- =============================================================================
CREATE TABLE Votantes (
    identificacion  VARCHAR2(20)  NOT NULL,
    correo          VARCHAR2(100) NOT NULL,
    primer_nombre   VARCHAR2(50)  NOT NULL,
    segundo_nombre  VARCHAR2(50),
    primer_apellido VARCHAR2(50)  NOT NULL,
    segundo_apellido VARCHAR2(50),
    estado_voto     VARCHAR2(15)  DEFAULT 'PENDIENTE' NOT NULL,
    foto_url        VARCHAR2(255),
    fecha_consentimiento TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    qr_cedula       VARCHAR2(500),
    id_rol          NUMBER        NOT NULL,
    id_puesto       NUMBER        NOT NULL
);

ALTER TABLE Votantes
    ADD CONSTRAINT pk_votantes
    PRIMARY KEY (identificacion);

ALTER TABLE Votantes
    ADD CONSTRAINT uq_votantes_correo
    UNIQUE (correo);

ALTER TABLE Votantes
    ADD CONSTRAINT chk_votantes_estado_voto
    CHECK (estado_voto IN ('PENDIENTE', 'EJERCIDO', 'INHABILITADO'));


-- =============================================================================
-- 4. BIOMETRIA_VOTANTES
-- (Datos biométricos separados de Votantes para control de acceso diferenciado)
-- =============================================================================
CREATE TABLE Biometria_votantes (
    id_biometria             NUMBER        NOT NULL,
    identificacion           VARCHAR2(20)  NOT NULL,
    plantilla_biometrica     BLOB          NOT NULL,
    hash_integridad_biometrica VARCHAR2(256) NOT NULL,
    fecha_enrolamiento       DATE          DEFAULT SYSDATE NOT NULL,
    activo                   VARCHAR2(1)   DEFAULT 'S' NOT NULL
);

ALTER TABLE Biometria_votantes
    ADD CONSTRAINT pk_biometria_votantes
    PRIMARY KEY (id_biometria);

ALTER TABLE Biometria_votantes
    ADD CONSTRAINT uq_biometria_votantes_identificacion
    UNIQUE (identificacion);

ALTER TABLE Biometria_votantes
    ADD CONSTRAINT chk_biometria_votantes_activo
    CHECK (activo IN ('S', 'N'));


-- =============================================================================
-- 5. MESA_JURADOS
-- =============================================================================
CREATE TABLE Mesa_jurados (
    id_mesa     NUMBER        NOT NULL,
    hora_ingreso TIMESTAMP    NOT NULL,
    hora_salida  TIMESTAMP,
    cargo        VARCHAR2(40) NOT NULL,
    id_puesto    NUMBER       NOT NULL
);

ALTER TABLE Mesa_jurados
    ADD CONSTRAINT pk_mesa_jurados
    PRIMARY KEY (id_mesa);

ALTER TABLE Mesa_jurados
    ADD CONSTRAINT chk_mesa_jurados_horas
    CHECK (hora_salida > hora_ingreso);


-- =============================================================================
-- 6. JURADOS
-- (Tabla asociativa: Votantes <-> Mesa_jurados)
-- =============================================================================
CREATE TABLE Jurados (
    id_mesa         NUMBER        NOT NULL,
    identificacion  VARCHAR2(20)  NOT NULL,
    fecha_asignacion DATE         NOT NULL,
    cargo           VARCHAR2(40)  NOT NULL
);

ALTER TABLE Jurados
    ADD CONSTRAINT pk_jurados
    PRIMARY KEY (id_mesa, identificacion);


-- =============================================================================
-- 7. ELECCIONES
-- =============================================================================
CREATE TABLE Elecciones (
    id_eleccion      NUMBER        NOT NULL,
    nombre           VARCHAR2(200) NOT NULL,
    fecha_hora_inicio TIMESTAMP    NOT NULL,
    fecha_hora_fin    TIMESTAMP    NOT NULL,
    estado           VARCHAR2(15)  DEFAULT 'PROGRAMADA' NOT NULL
);

ALTER TABLE Elecciones
    ADD CONSTRAINT pk_elecciones
    PRIMARY KEY (id_eleccion);

ALTER TABLE Elecciones
    ADD CONSTRAINT chk_elecciones_estado
    CHECK (estado IN ('PROGRAMADA', 'EN_CURSO', 'CERRADA'));

ALTER TABLE Elecciones
    ADD CONSTRAINT chk_elecciones_fechas
    CHECK (fecha_hora_fin > fecha_hora_inicio);


-- =============================================================================
-- 8. ELECCION_ROLES
-- (Configuración del peso de voto por rol en cada elección)
-- =============================================================================
CREATE TABLE Eleccion_roles (
    id_eleccion         NUMBER      NOT NULL,
    id_rol              NUMBER      NOT NULL,
    peso_voto           NUMBER(5,2) NOT NULL,
    fecha_configuracion DATE        DEFAULT SYSDATE NOT NULL
);

ALTER TABLE Eleccion_roles
    ADD CONSTRAINT pk_eleccion_roles
    PRIMARY KEY (id_eleccion, id_rol);

ALTER TABLE Eleccion_roles
    ADD CONSTRAINT chk_eleccion_roles_peso
    CHECK (peso_voto > 0);


-- =============================================================================
-- 9. CANDIDATOS
-- =============================================================================
CREATE TABLE Candidatos (
    id_candidato     NUMBER       NOT NULL,
    primer_nombre    VARCHAR2(50) NOT NULL,
    segundo_nombre   VARCHAR2(50),
    primer_apellido  VARCHAR2(50) NOT NULL,
    segundo_apellido VARCHAR2(50)
);

ALTER TABLE Candidatos
    ADD CONSTRAINT pk_candidatos
    PRIMARY KEY (id_candidato);


-- =============================================================================
-- 10. CANDIDATOS_ELECCION
-- (Tabla intermedia: un candidato puede postularse en múltiples elecciones)
-- =============================================================================
CREATE TABLE Candidatos_eleccion (
    id_candidato    NUMBER        NOT NULL,
    id_eleccion     NUMBER        NOT NULL,
    numero_campania NUMBER(4)     NOT NULL,
    cargo           VARCHAR2(100) NOT NULL
);

ALTER TABLE Candidatos_eleccion
    ADD CONSTRAINT pk_candidatos_eleccion
    PRIMARY KEY (id_candidato, id_eleccion);

ALTER TABLE Candidatos_eleccion
    ADD CONSTRAINT uq_candidatos_eleccion_numero
    UNIQUE (id_eleccion, numero_campania);


-- =============================================================================
-- 11. VOTOS
-- (Registro anonimizado del voto; no vinculado directamente al votante)
-- =============================================================================
CREATE TABLE Votos (
    id_voto          NUMBER      NOT NULL,
    fecha_hora       TIMESTAMP   DEFAULT SYSTIMESTAMP NOT NULL,
    peso_voto_aplicado NUMBER(5,2) NOT NULL,
    id_eleccion      NUMBER      NOT NULL,
    id_candidato     NUMBER      NOT NULL
);

ALTER TABLE Votos
    ADD CONSTRAINT pk_votos
    PRIMARY KEY (id_voto);

ALTER TABLE Votos
    ADD CONSTRAINT chk_votos_peso_positivo
    CHECK (peso_voto_aplicado > 0);


-- =============================================================================
-- 12. REGISTRO_VOTOS
-- (Audit trail: constancia de participación sin revelar el candidato elegido)
-- =============================================================================
CREATE TABLE Registro_votos (
    id_registro    NUMBER        NOT NULL,
    fecha_hora     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    identificacion VARCHAR2(20)  NOT NULL,
    id_puesto      NUMBER        NOT NULL,
    id_eleccion    NUMBER        NOT NULL
);

ALTER TABLE Registro_votos
    ADD CONSTRAINT pk_registro_votos
    PRIMARY KEY (id_registro);

ALTER TABLE Registro_votos
    ADD CONSTRAINT uq_registro_votos_votante_eleccion
    UNIQUE (identificacion, id_eleccion);


-- =============================================================================
-- 13. ADMINISTRADORES
-- =============================================================================
CREATE TABLE Administradores (
    id_admin      NUMBER        NOT NULL,
    usuario       VARCHAR2(40)  NOT NULL,
    password_hash VARCHAR2(256) NOT NULL,
    nombre        VARCHAR2(100) NOT NULL,
    correo        VARCHAR2(100) NOT NULL
);

ALTER TABLE Administradores
    ADD CONSTRAINT pk_administradores
    PRIMARY KEY (id_admin);

ALTER TABLE Administradores
    ADD CONSTRAINT uq_administradores_usuario
    UNIQUE (usuario);

ALTER TABLE Administradores
    ADD CONSTRAINT uq_administradores_correo
    UNIQUE (correo);


-- =============================================================================
-- 14. SESIONES
-- =============================================================================
CREATE TABLE Sesiones (
    id_sesion    NUMBER        NOT NULL,
    token        VARCHAR2(255) NOT NULL,
    fecha_inicio TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    fecha_fin    TIMESTAMP,
    id_admin     NUMBER        NOT NULL
);

ALTER TABLE Sesiones
    ADD CONSTRAINT pk_sesiones
    PRIMARY KEY (id_sesion);

ALTER TABLE Sesiones
    ADD CONSTRAINT uq_sesiones_token
    UNIQUE (token);


-- =============================================================================
-- 15. AUDITORIA_VOTANTES
-- =============================================================================
CREATE TABLE Auditoria_votantes (
    id_auditoria     NUMBER        NOT NULL,
    identificacion   VARCHAR2(20)  NOT NULL,
    id_admin         NUMBER        NOT NULL,
    campo_modificado VARCHAR2(50)  NOT NULL,
    valor_anterior   VARCHAR2(500),
    valor_nuevo      VARCHAR2(500),
    motivo           VARCHAR2(255),
    accion           VARCHAR2(30)  NOT NULL,
    fecha_hora       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
);

ALTER TABLE Auditoria_votantes
    ADD CONSTRAINT pk_auditoria_votantes
    PRIMARY KEY (id_auditoria);

ALTER TABLE Auditoria_votantes
    ADD CONSTRAINT chk_auditoria_votantes_accion
    CHECK (accion IN (
        'EDICION_DATOS',
        'CAMBIO_ROL',
        'CAMBIO_PUESTO',
        'INHABILITACION',
        'HABILITACION',
        'RE_ENROLAMIENTO',
        'ANONIMIZACION',
        'RESET_ESTADO_VOTO'
    ));


-- =============================================================================
-- 16. AUDITORIA_CORREOS
-- (Audit trail operativo del envio de certificados a votantes; no guarda seleccion de voto)
-- =============================================================================
CREATE TABLE Auditoria_correos (
    id_auditoria       NUMBER        NOT NULL,
    identificacion     VARCHAR2(20)  NOT NULL,
    id_eleccion        NUMBER        NOT NULL,
    correo_votante     VARCHAR2(100) NOT NULL,
    fecha_solicitud    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    fecha_envio        TIMESTAMP,
    estado             VARCHAR2(30)  NOT NULL,
    provider           VARCHAR2(30)  DEFAULT 'RESEND' NOT NULL,
    message_id         VARCHAR2(120),
    codigo_certificado VARCHAR2(80)  NOT NULL,
    observaciones      VARCHAR2(500)
);

ALTER TABLE Auditoria_correos
    ADD CONSTRAINT pk_auditoria_correos
    PRIMARY KEY (id_auditoria);

ALTER TABLE Auditoria_correos
    ADD CONSTRAINT chk_auditoria_correos_estado
    CHECK (estado IN (
        'SOLICITADO',
        'ENVIADO',
        'ERROR',
        'PENDIENTE_REINTENTO'
    ));

-- =============================================================================
-- FIN DEL SCRIPT
-- =============================================================================
