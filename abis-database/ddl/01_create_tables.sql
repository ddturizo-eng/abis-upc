-- =============================================================================
-- ABIS-UPC | DDL - Creación de Tablas
-- Universidad Popular del Cesar | Programación III | 2026
-- Autor: Jorge Iván Herrera García
-- Revisado por: Daniel Turizo (Líder)
-- Base de datos: Oracle Database XE
-- Ejecutar en orden. Roles ya fue creada, se incluye para referencia.
-- =============================================================================


-- =============================================================================
-- 1. ROLES (ya creada - referencia)
-- =============================================================================
-- CREATE TABLE Roles (
--     idRol    NUMBER,
--     nombre   VARCHAR2(20)  NOT NULL UNIQUE,
--     pesoVoto NUMBER(5,2)   NOT NULL
-- );
-- ALTER TABLE Roles ADD CONSTRAINT pk_idRol_roles PRIMARY KEY (idRol);


-- =============================================================================
-- 2. PUESTOS_VOTACION
-- =============================================================================
CREATE TABLE Puestos_votacion (
    idPuestos     NUMBER,
    ciudad        VARCHAR2(60)  NOT NULL,
    sede          VARCHAR2(100) NOT NULL,
    nombrePuesto  VARCHAR2(80)  NOT NULL,
    horaInicio    TIMESTAMP     NOT NULL,
    horaSalida    TIMESTAMP     NOT NULL
);

ALTER TABLE Puestos_votacion
    ADD CONSTRAINT pk_idPuestos_puestosvotacion PRIMARY KEY (idPuestos);


-- =============================================================================
-- 3. VOTANTES
-- =============================================================================
CREATE TABLE Votantes (
    identificacion             VARCHAR2(20),
    plantillaBiometrica        VARCHAR2(255),
    correo                     VARCHAR2(100) NOT NULL UNIQUE,
    primerNombre               VARCHAR2(50)  NOT NULL,
    segundoNombre              VARCHAR2(50),
    primerApellido             VARCHAR2(50)  NOT NULL,
    segundoApellido            VARCHAR2(50),
    estadoVoto                 VARCHAR2(15)  DEFAULT 'PENDIENTE' NOT NULL,
    fotoUrl                    VARCHAR2(255),
    fechaConsentimiento        DATE,
    hashIntegridadBiometrica   VARCHAR2(64),
    Roles_idRol                NUMBER        NOT NULL,
    Puestos_votacion_idPuestos NUMBER        NOT NULL,
    CONSTRAINT chk_estadoVoto  CHECK (estadoVoto IN ('PENDIENTE','EJERCIDO','INHABILITADO'))
    -- NOTA: Si un votante es jurado se determina por su existencia
    --       en la tabla Jurados, NO por un atributo aqui.
);

ALTER TABLE Votantes
    ADD CONSTRAINT pk_identificacion_votantes PRIMARY KEY (identificacion);

ALTER TABLE Votantes
    ADD CONSTRAINT fk_votantes_roles
    FOREIGN KEY (Roles_idRol)
    REFERENCES Roles (idRol);

ALTER TABLE Votantes
    ADD CONSTRAINT fk_votantes_puestos
    FOREIGN KEY (Puestos_votacion_idPuestos)
    REFERENCES Puestos_votacion (idPuestos);


-- =============================================================================
-- 4. MESA_JURADOS
-- =============================================================================
CREATE TABLE Mesa_jurados (
    idMesa                     NUMBER,
    horaIngreso                TIMESTAMP     NOT NULL,
    horaSalida                 TIMESTAMP,
    cargo                      VARCHAR2(40)  NOT NULL,
    Puestos_votacion_idPuestos NUMBER        NOT NULL
);

ALTER TABLE Mesa_jurados
    ADD CONSTRAINT pk_idMesa_mesajurados PRIMARY KEY (idMesa);

ALTER TABLE Mesa_jurados
    ADD CONSTRAINT fk_mesajurados_puestos
    FOREIGN KEY (Puestos_votacion_idPuestos)
    REFERENCES Puestos_votacion (idPuestos);


-- =============================================================================
-- 5. JURADOS
-- (Tabla asociativa: Votantes <-> Mesa_jurados)
-- =============================================================================
CREATE TABLE Jurados (
    fechaAsignacion          DATE          NOT NULL,
    cargo                    VARCHAR2(40)  NOT NULL,
    Mesa_jurados_idMesa      NUMBER        NOT NULL,
    votantes_identificacion  VARCHAR2(20)  NOT NULL
);

ALTER TABLE Jurados
    ADD CONSTRAINT pk_idMesa_identificacion_jurados PRIMARY KEY (Mesa_jurados_idMesa, votantes_identificacion);

ALTER TABLE Jurados
    ADD CONSTRAINT fk_jurados_mesa
    FOREIGN KEY (Mesa_jurados_idMesa)
    REFERENCES Mesa_jurados (idMesa);

ALTER TABLE Jurados
    ADD CONSTRAINT fk_jurados_votantes
    FOREIGN KEY (votantes_identificacion)
    REFERENCES Votantes (identificacion);


-- =============================================================================
-- 6. ELECCIONES
-- =============================================================================
CREATE TABLE Elecciones (
    idEleccion       NUMBER,
    fechaHoraInicio  TIMESTAMP    NOT NULL,
    fechaHoraFin     TIMESTAMP    NOT NULL,
    estado           VARCHAR2(15) DEFAULT 'PROGRAMADA' NOT NULL,
    CONSTRAINT chk_estado_eleccion CHECK (estado IN ('PROGRAMADA', 'EN_CURSO', 'CERRADA'))
);

ALTER TABLE Elecciones
    ADD CONSTRAINT pk_idEleccion_elecciones PRIMARY KEY (idEleccion);


-- =============================================================================
-- 7. CANDIDATOS
-- =============================================================================
CREATE TABLE Candidatos (
    idCandidato      NUMBER,
    primerNombre     VARCHAR2(50)  NOT NULL,
    segundoNombre    VARCHAR2(50),
    primerApellido   VARCHAR2(50)  NOT NULL,
    segundoApellido  VARCHAR2(50),
    numeroCampaña    NUMBER(4)     NOT NULL UNIQUE,
    Elecciones_idEleccion NUMBER  NOT NULL,
    Votos_idVotos    NUMBER
);

ALTER TABLE Candidatos
    ADD CONSTRAINT pk_idCandidato_candidatos PRIMARY KEY (idCandidato);

ALTER TABLE Candidatos
    ADD CONSTRAINT fk_candidatos_elecciones
    FOREIGN KEY (Elecciones_idEleccion)
    REFERENCES Elecciones (idEleccion);


-- =============================================================================
-- 8. VOTOS
-- (Registro anonimizado del voto; no vinculado al votante por integridad)
-- =============================================================================
CREATE TABLE Votos (
    idVotos              NUMBER,
    Roles_idRol          NUMBER NOT NULL,
    Elecciones_idEleccion NUMBER NOT NULL
);

ALTER TABLE Votos
    ADD CONSTRAINT pk_idVotos_votos PRIMARY KEY (idVotos);

ALTER TABLE Votos
    ADD CONSTRAINT fk_votos_roles
    FOREIGN KEY (Roles_idRol)
    REFERENCES Roles (idRol);

ALTER TABLE Votos
    ADD CONSTRAINT fk_votos_elecciones
    FOREIGN KEY (Elecciones_idEleccion)
    REFERENCES Elecciones (idEleccion);

-- FK diferida: Candidatos referencia a Votos (se agrega después de crear Votos)
ALTER TABLE Candidatos
    ADD CONSTRAINT fk_candidatos_votos
    FOREIGN KEY (Votos_idVotos)
    REFERENCES Votos (idVotos);


-- =============================================================================
-- 9. REGISTRO_VOTOS
-- (Audit trail: qué votante en qué puesto, sin revelar a quién votó)
-- =============================================================================
CREATE TABLE Registro_votos (
    idRegistro                 NUMBER,
    fechaHora                  TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    votantes_identificacion    VARCHAR2(20) NOT NULL,
    Puestos_votacion_idPuestos NUMBER       NOT NULL
);

ALTER TABLE Registro_votos
    ADD CONSTRAINT pk_idRegistro_registrovotos PRIMARY KEY (idRegistro);

ALTER TABLE Registro_votos
    ADD CONSTRAINT fk_registrovotos_votantes
    FOREIGN KEY (votantes_identificacion)
    REFERENCES Votantes (identificacion);

ALTER TABLE Registro_votos
    ADD CONSTRAINT fk_registrovotos_puestos
    FOREIGN KEY (Puestos_votacion_idPuestos)
    REFERENCES Puestos_votacion (idPuestos);

-- Restricción: un votante solo puede tener UN registro de voto
ALTER TABLE Registro_votos
    ADD CONSTRAINT uq_registro_votante UNIQUE (votantes_identificacion);


-- =============================================================================
-- 10. ADMINISTRADORES
-- =============================================================================
CREATE TABLE Administradores (
    idAdmin       NUMBER,
    usuario       VARCHAR2(40)  NOT NULL UNIQUE,
    passwordHash  VARCHAR2(64)  NOT NULL,
    nombre        VARCHAR2(100) NOT NULL
);

ALTER TABLE Administradores
    ADD CONSTRAINT pk_idAdmin_administradores PRIMARY KEY (idAdmin);


-- =============================================================================
-- 11. SESIONES
-- =============================================================================
CREATE TABLE Sesiones (
    idSesion               NUMBER,
    token                  VARCHAR2(255) NOT NULL UNIQUE,
    fechaInicio            TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    Administradores_idAdmin NUMBER       NOT NULL
);

ALTER TABLE Sesiones
    ADD CONSTRAINT pk_idSesion_sesiones PRIMARY KEY (idSesion);

ALTER TABLE Sesiones
    ADD CONSTRAINT fk_sesiones_admin
    FOREIGN KEY (Administradores_idAdmin)
    REFERENCES Administradores (idAdmin);


-- =============================================================================
-- SEQUENCES (para auto-incremento en Oracle XE sin IDENTITY)
-- =============================================================================
CREATE SEQUENCE seq_puestos_votacion  START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE seq_mesa_jurados      START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE seq_elecciones        START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE seq_candidatos        START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE seq_votos             START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE seq_registro_votos    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE seq_administradores   START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE seq_sesiones          START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE seq_roles             START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- =============================================================================
-- FIN DEL SCRIPT DDL
-- =============================================================================
