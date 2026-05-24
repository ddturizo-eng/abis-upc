-- ============================================================================
-- Nombre: 01_modificaciones_tablas.sql
-- Descripcion: Modificaciones estructurales a tablas del esquema ABISADMIN
--              ALTER TABLE, DROP CONSTRAINT para ajustar el modelo de datos
-- Fecha: Mayo 2026
-- ============================================================================

-- ============================================================================
-- 1.1 VOTOS — Eliminar ID_ROL y permitir voto en blanco
-- Razón: ID_ROL en VOTOS rompía el secreto del sufragio. ID_CANDIDATO debe 
--        ser nullable para soportar voto en blanco (derecho constitucional)
-- ============================================================================

-- Eliminar FK que dependía de ID_ROL
ALTER TABLE VOTOS DROP CONSTRAINT FK_VOTOS_ROLES;

-- Eliminar columna ID_ROL
ALTER TABLE VOTOS MODIFY (ID_ROL NULL);
ALTER TABLE VOTOS DROP COLUMN ID_ROL;

-- Permitir voto en blanco
ALTER TABLE VOTOS MODIFY (ID_CANDIDATO NULL);

-- ============================================================================
-- 1.2 PUESTOS_VOTACION — Agregar horario electoral
-- Razón: Sin horas de apertura y cierre no se puede validar que los votos 
--        ocurran dentro del horario permitido
-- ============================================================================

ALTER TABLE PUESTOS_VOTACION ADD (
    HORA_INICIO TIMESTAMP,
    HORA_SALIDA TIMESTAMP
);

-- Poblar con horario real antes de aplicar NOT NULL
UPDATE PUESTOS_VOTACION
SET HORA_INICIO = TIMESTAMP '2026-05-17 08:00:00',
    HORA_SALIDA = TIMESTAMP '2026-05-17 16:00:00';
COMMIT;

ALTER TABLE PUESTOS_VOTACION MODIFY (HORA_INICIO NOT NULL);
ALTER TABLE PUESTOS_VOTACION MODIFY (HORA_SALIDA NOT NULL);

ALTER TABLE PUESTOS_VOTACION ADD CONSTRAINT CHK_PUESTOS_HORAS
CHECK (HORA_SALIDA > HORA_INICIO);

-- ============================================================================
-- 1.3 AUDITORIA_VOTANTES — Migrar FECHA_HORA de DATE a TIMESTAMP
-- Razón: DATE solo guarda hasta segundos. Los eventos de auditoría y biometría 
--        requieren precisión de microsegundos
-- ============================================================================

-- Agregar columna temporal
ALTER TABLE AUDITORIA_VOTANTES ADD (FECHA_HORA_TS TIMESTAMP);

-- Migrar datos existentes
UPDATE AUDITORIA_VOTANTES
SET FECHA_HORA_TS = CAST(FECHA_HORA AS TIMESTAMP);
COMMIT;

-- Eliminar columna DATE original
ALTER TABLE AUDITORIA_VOTANTES DROP COLUMN FECHA_HORA;

-- Renombrar y aplicar NOT NULL
ALTER TABLE AUDITORIA_VOTANTES RENAME COLUMN FECHA_HORA_TS TO FECHA_HORA;
ALTER TABLE AUDITORIA_VOTANTES MODIFY (FECHA_HORA NOT NULL);

-- ============================================================================
-- 1.4 AUDITORIA_VOTANTES — Actualizar CHECK de acciones válidas
-- Razón: El constraint original no incluía RESET_ESTADO_VOTO, bloqueando 
--        silenciosamente registros legítimos del trigger de auditoría
-- ============================================================================

ALTER TABLE AUDITORIA_VOTANTES
DROP CONSTRAINT CHK_ACCION_AUDITORIA_VOTANTES;

ALTER TABLE AUDITORIA_VOTANTES
ADD CONSTRAINT CHK_ACCION_AUDITORIA_VOTANTES
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

-- ============================================================================
-- 1.5 BIOMETRIA_VOTANTES — Eliminar UNIQUE en identificacion
-- Razón: El UNIQUE impedía el re-enrolamiento biométrico. El historial se 
--        maneja con la columna ACTIVO — un votante puede tener múltiples 
--        registros biométricos, pero solo uno activo a la vez
-- ============================================================================

ALTER TABLE BIOMETRIA_VOTANTES
DROP CONSTRAINT UQ_BIOMETRIA_VOTANTES_IDENTIFICACION;

-- ============================================================================
-- 1.6 MESA_JURADOS — Agregar ID_ELECCION y FK a ELECCIONES
-- Razón: Para validar RN-04: un votante no puede ser jurado en dos mesas
--        de la misma elección. También permite filtrar mesas por elección.
-- ============================================================================

ALTER TABLE MESA_JURADOS ADD ID_ELECCION NUMBER;
ALTER TABLE MESA_JURADOS ADD CONSTRAINT fk_mesa_eleccion
    FOREIGN KEY (ID_ELECCION) REFERENCES ELECCIONES(ID_ELECCION);

-- ============================================================================
-- 1.7 TRG_JURADO_NO_DUPLICADO — Trigger BEFORE INSERT en JURADOS
-- Razón: RN-04 — Un votante no puede ser jurado en dos mesas de la misma
--        elección. El trigger valida antes de cada INSERT.
-- ============================================================================

CREATE OR REPLACE TRIGGER trg_jurado_no_duplicado
BEFORE INSERT ON JURADOS
FOR EACH ROW
DECLARE
    v_id_eleccion  MESA_JURADOS.id_eleccion%TYPE;
    v_existe       NUMBER;
    v_otra_mesa    MESA_JURADOS.id_mesa%TYPE;
BEGIN
    SELECT id_eleccion
    INTO   v_id_eleccion
    FROM   MESA_JURADOS
    WHERE  id_mesa = :NEW.mesa_jurados_idmesa;

    SELECT COUNT(*), MAX(j.mesa_jurados_idmesa)
    INTO   v_existe, v_otra_mesa
    FROM   JURADOS j
    JOIN   MESA_JURADOS m ON j.mesa_jurados_idmesa = m.id_mesa
    WHERE  j.votantes_identificacion = :NEW.votantes_identificacion
      AND  m.id_eleccion = v_id_eleccion
      AND  j.mesa_jurados_idmesa != :NEW.mesa_jurados_idmesa;

    IF v_existe > 0 THEN
        RAISE_APPLICATION_ERROR(-20080,
            'RN-04: El votante ' || :NEW.votantes_identificacion ||
            ' ya es jurado en la mesa ' || v_otra_mesa ||
            ' de la misma eleccion.');
    END IF;
END trg_jurado_no_duplicado;
/