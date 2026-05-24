-- =============================================================================
-- ABIS-UPC | DML - Datos maestros iniciales
-- Universidad Popular del Cesar | Programación III | 2026
-- Autor: Jorge Iván Herrera García
-- Base de datos: Oracle Database XE
-- Descripción: Inserta los datos de referencia necesarios para operar
--              el sistema (roles, puestos, administrador principal).
--              Ejecutar después de todos los scripts DDL.
-- =============================================================================


-- =============================================================================
-- ROLES
-- =============================================================================
INSERT INTO Roles (id_rol, nombre)
VALUES (seq_roles.NEXTVAL, 'Estudiante');

INSERT INTO Roles (id_rol, nombre)
VALUES (seq_roles.NEXTVAL, 'Docente');

INSERT INTO Roles (id_rol, nombre)
VALUES (seq_roles.NEXTVAL, 'Egresado');

INSERT INTO Roles (id_rol, nombre)
VALUES (seq_roles.NEXTVAL, 'Administrativo');


-- =============================================================================
-- PUESTOS DE VOTACION
-- =============================================================================
INSERT INTO Puestos_votacion (id_puesto, ciudad, sede, nombre_puesto, hora_inicio, hora_salida)
VALUES (seq_puestos_votacion.NEXTVAL, 'Valledupar', 'Sede Sabanas', 'Sala de Sistemas 204',
        TO_TIMESTAMP('2026-01-01 08:00:00', 'YYYY-MM-DD HH24:MI:SS'),
        TO_TIMESTAMP('2026-01-01 18:00:00', 'YYYY-MM-DD HH24:MI:SS'));

INSERT INTO Puestos_votacion (id_puesto, ciudad, sede, nombre_puesto, hora_inicio, hora_salida)
VALUES (seq_puestos_votacion.NEXTVAL, 'Valledupar', 'Sede Hurtado', 'Auditorio Principal',
        TO_TIMESTAMP('2026-01-01 08:00:00', 'YYYY-MM-DD HH24:MI:SS'),
        TO_TIMESTAMP('2026-01-01 18:00:00', 'YYYY-MM-DD HH24:MI:SS'));

INSERT INTO Puestos_votacion (id_puesto, ciudad, sede, nombre_puesto, hora_inicio, hora_salida)
VALUES (seq_puestos_votacion.NEXTVAL, 'Aguachica', 'Sede Aguachica', 'Centro de Convenciones',
        TO_TIMESTAMP('2026-01-01 08:00:00', 'YYYY-MM-DD HH24:MI:SS'),
        TO_TIMESTAMP('2026-01-01 18:00:00', 'YYYY-MM-DD HH24:MI:SS'));


-- =============================================================================
-- ADMINISTRADOR PRINCIPAL
-- password_hash: SHA-256 de la contraseña inicial (cambiar en producción)
-- =============================================================================
INSERT INTO Administradores (id_admin, usuario, password_hash, nombre, correo)
VALUES (seq_administradores.NEXTVAL,
        'abisadmin',
        '8737f1c27602dd297b416cd4bc42707b3b96e8a26923d29bfccfb395cefc2339',
        'Administrador Principal',
        'ddturizo@unicesar.edu.co');

COMMIT;

-- =============================================================================
-- FIN DEL SCRIPT
-- =============================================================================
