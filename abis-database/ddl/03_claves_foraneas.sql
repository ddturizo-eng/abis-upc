-- =============================================================================
-- ABIS-UPC | DDL - Claves foráneas
-- Universidad Popular del Cesar | Programación III | 2026
-- Autor: Jorge Iván Herrera García
-- Base de datos: Oracle Database XE
-- Descripción: Define todas las claves foráneas del modelo relacional.
--              Ejecutar después de 02_tablas.sql.
--              Orden: tabla hija referencia tabla padre.
-- =============================================================================


-- =============================================================================
-- VOTANTES
-- =============================================================================

ALTER TABLE Votantes
    ADD CONSTRAINT fk_votantes_roles
    FOREIGN KEY (id_rol)
    REFERENCES Roles (id_rol);

ALTER TABLE Votantes
    ADD CONSTRAINT fk_votantes_puestos_votacion
    FOREIGN KEY (id_puesto)
    REFERENCES Puestos_votacion (id_puesto);


-- =============================================================================
-- BIOMETRIA_VOTANTES
-- =============================================================================

ALTER TABLE Biometria_votantes
    ADD CONSTRAINT fk_biometria_votantes_votantes
    FOREIGN KEY (identificacion)
    REFERENCES Votantes (identificacion);


-- =============================================================================
-- MESA_JURADOS
-- =============================================================================

ALTER TABLE Mesa_jurados
    ADD CONSTRAINT fk_mesa_jurados_puestos_votacion
    FOREIGN KEY (id_puesto)
    REFERENCES Puestos_votacion (id_puesto);


-- =============================================================================
-- JURADOS
-- =============================================================================

ALTER TABLE Jurados
    ADD CONSTRAINT fk_jurados_mesa_jurados
    FOREIGN KEY (id_mesa)
    REFERENCES Mesa_jurados (id_mesa);

ALTER TABLE Jurados
    ADD CONSTRAINT fk_jurados_votantes
    FOREIGN KEY (identificacion)
    REFERENCES Votantes (identificacion);


-- =============================================================================
-- ELECCION_ROLES
-- =============================================================================

ALTER TABLE Eleccion_roles
    ADD CONSTRAINT fk_eleccion_roles_elecciones
    FOREIGN KEY (id_eleccion)
    REFERENCES Elecciones (id_eleccion);

ALTER TABLE Eleccion_roles
    ADD CONSTRAINT fk_eleccion_roles_roles
    FOREIGN KEY (id_rol)
    REFERENCES Roles (id_rol);


-- =============================================================================
-- CANDIDATOS_ELECCION
-- =============================================================================

ALTER TABLE Candidatos_eleccion
    ADD CONSTRAINT fk_candidatos_eleccion_candidatos
    FOREIGN KEY (id_candidato)
    REFERENCES Candidatos (id_candidato);

ALTER TABLE Candidatos_eleccion
    ADD CONSTRAINT fk_candidatos_eleccion_elecciones
    FOREIGN KEY (id_eleccion)
    REFERENCES Elecciones (id_eleccion);


-- =============================================================================
-- VOTOS
-- (FK compuesta hacia Candidatos_eleccion — Oracle exige referenciar
--  todos los campos de la PK compuesta)
-- =============================================================================

ALTER TABLE Votos
    ADD CONSTRAINT fk_votos_candidatos_eleccion
    FOREIGN KEY (id_candidato, id_eleccion)
    REFERENCES Candidatos_eleccion (id_candidato, id_eleccion);


-- =============================================================================
-- REGISTRO_VOTOS
-- =============================================================================

ALTER TABLE Registro_votos
    ADD CONSTRAINT fk_registro_votos_votantes
    FOREIGN KEY (identificacion)
    REFERENCES Votantes (identificacion);

ALTER TABLE Registro_votos
    ADD CONSTRAINT fk_registro_votos_puestos_votacion
    FOREIGN KEY (id_puesto)
    REFERENCES Puestos_votacion (id_puesto);

ALTER TABLE Registro_votos
    ADD CONSTRAINT fk_registro_votos_elecciones
    FOREIGN KEY (id_eleccion)
    REFERENCES Elecciones (id_eleccion);


-- =============================================================================
-- SESIONES
-- =============================================================================

ALTER TABLE Sesiones
    ADD CONSTRAINT fk_sesiones_administradores
    FOREIGN KEY (id_admin)
    REFERENCES Administradores (id_admin);


-- =============================================================================
-- AUDITORIA_VOTANTES
-- =============================================================================

ALTER TABLE Auditoria_votantes
    ADD CONSTRAINT fk_auditoria_votantes_votantes
    FOREIGN KEY (identificacion)
    REFERENCES Votantes (identificacion);

ALTER TABLE Auditoria_votantes
    ADD CONSTRAINT fk_auditoria_votantes_administradores
    FOREIGN KEY (id_admin)
    REFERENCES Administradores (id_admin);


-- =============================================================================
-- AUDITORIA_CORREOS
-- =============================================================================

ALTER TABLE Auditoria_correos
    ADD CONSTRAINT fk_auditoria_correos_votantes
    FOREIGN KEY (identificacion)
    REFERENCES Votantes (identificacion);

ALTER TABLE Auditoria_correos
    ADD CONSTRAINT fk_auditoria_correos_elecciones
    FOREIGN KEY (id_eleccion)
    REFERENCES Elecciones (id_eleccion);

-- =============================================================================
-- FIN DEL SCRIPT
-- =============================================================================
