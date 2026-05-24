-- =============================================================================
-- ABIS-UPC | DDL - Secuencias de auto-incremento
-- Universidad Popular del Cesar | Programación III | 2026
-- Autor: Jorge Iván Herrera García
-- Base de datos: Oracle Database XE
-- Descripción: Define todas las secuencias usadas como PK artificial.
--              Ejecutar antes que cualquier otro script DDL.
-- =============================================================================

CREATE SEQUENCE seq_roles
    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE SEQUENCE seq_puestos_votacion
    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE SEQUENCE seq_mesa_jurados
    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE SEQUENCE seq_elecciones
    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE SEQUENCE seq_candidatos
    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE SEQUENCE seq_votos
    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE SEQUENCE seq_registro_votos
    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE SEQUENCE seq_administradores
    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE SEQUENCE seq_sesiones
    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE SEQUENCE seq_auditoria_votantes
    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE SEQUENCE seq_biometria_votantes
    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE SEQUENCE seq_auditoria_correos
    START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- =============================================================================
-- FIN DEL SCRIPT
-- =============================================================================
