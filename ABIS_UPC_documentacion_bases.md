# ABIS-UPC — Documentación Técnica de Implementación PL/SQL
**Sistema de Votación Electrónica con Autenticación Biométrica**  
**Universidad Popular del Cesar — Ingeniería de Sistemas**  
**Base de datos: Oracle — Esquema: ABISADMIN**

---

## Índice

1. [Modificaciones estructurales a tablas](#1-modificaciones-estructurales-a-tablas)
2. [Datos de prueba](#2-datos-de-prueba)
3. [Triggers](#3-triggers)
4. [Funciones almacenadas](#4-funciones-almacenadas)
5. [Procedimientos almacenados](#5-procedimientos-almacenados)
6. [Referencia para integración con backend](#6-referencia-para-integración-con-backend)

---

## 1. Modificaciones estructurales a tablas

### 1.1 VOTOS — Eliminar ID_ROL y permitir voto en blanco

**Razón:** `ID_ROL` en VOTOS rompía el secreto del sufragio. `ID_CANDIDATO` debe ser nullable para soportar voto en blanco (derecho constitucional).

```sql
-- Eliminar FK que dependía de ID_ROL
ALTER TABLE VOTOS DROP CONSTRAINT FK_VOTOS_ROLES;

-- Eliminar columna ID_ROL
ALTER TABLE VOTOS MODIFY (ID_ROL NULL);
ALTER TABLE VOTOS DROP COLUMN ID_ROL;

-- Permitir voto en blanco
ALTER TABLE VOTOS MODIFY (ID_CANDIDATO NULL);
```

### 1.2 PUESTOS_VOTACION — Agregar horario electoral

**Razón:** Sin horas de apertura y cierre no se puede validar que los votos ocurran dentro del horario permitido.

```sql
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
```

### 1.3 AUDITORIA_VOTANTES — Migrar FECHA_HORA de DATE a TIMESTAMP

**Razón:** DATE solo guarda hasta segundos. Los eventos de auditoría y biometría requieren precisión de microsegundos.

```sql
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
```

### 1.4 AUDITORIA_VOTANTES — Actualizar CHECK de acciones válidas

**Razón:** El constraint original no incluía `RESET_ESTADO_VOTO`, bloqueando silenciosamente registros legítimos del trigger de auditoría.

```sql
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
```

### 1.5 BIOMETRIA_VOTANTES — Eliminar UNIQUE en identificacion

**Razón:** El UNIQUE impedía el re-enrolamiento biométrico. El historial se maneja con la columna `ACTIVO` — un votante puede tener múltiples registros biométricos, pero solo uno activo a la vez.

```sql
ALTER TABLE BIOMETRIA_VOTANTES
DROP CONSTRAINT UQ_BIOMETRIA_VOTANTES_IDENTIFICACION;
```

---

## 2. Datos de prueba

```sql
-- ROLES
INSERT INTO ROLES (id_rol, nombre) VALUES (seq_roles.NEXTVAL, 'Estudiante');
INSERT INTO ROLES (id_rol, nombre) VALUES (seq_roles.NEXTVAL, 'Docente');
INSERT INTO ROLES (id_rol, nombre) VALUES (seq_roles.NEXTVAL, 'Egresado');
INSERT INTO ROLES (id_rol, nombre) VALUES (seq_roles.NEXTVAL, 'Administrativo');

-- ELECCIONES
UPDATE ELECCIONES SET estado = 'EN_CURSO' WHERE id_eleccion = 1;

INSERT INTO ELECCIONES (id_eleccion, nombre, fecha_hora_inicio, fecha_hora_fin, estado)
VALUES (seq_elecciones.NEXTVAL, 'Elección Consejo Estudiantil 2026',
        TIMESTAMP '2026-05-17 08:00:00',
        TIMESTAMP '2026-05-17 18:00:00', 'EN_CURSO');

-- CANDIDATOS
INSERT INTO CANDIDATOS (id_candidato, primer_nombre, primer_apellido,
                        segundo_nombre, segundo_apellido)
VALUES (seq_candidatos.NEXTVAL, 'Carlos', 'Mendoza', 'Andres', 'Perez');

INSERT INTO CANDIDATOS (id_candidato, primer_nombre, primer_apellido,
                        segundo_nombre, segundo_apellido)
VALUES (seq_candidatos.NEXTVAL, 'Laura', 'Gutierrez', 'Sofia', 'Rios');

INSERT INTO CANDIDATOS (id_candidato, primer_nombre, primer_apellido)
VALUES (seq_candidatos.NEXTVAL, 'Miguel', 'Torres');

-- CANDIDATOS_ELECCION
INSERT INTO CANDIDATOS_ELECCION (id_candidato, id_eleccion, numero_campania, cargo)
VALUES (2, 1, 1002, 'Presidente Consejo');
INSERT INTO CANDIDATOS_ELECCION (id_candidato, id_eleccion, numero_campania, cargo)
VALUES (3, 1, 1003, 'Presidente Consejo');
INSERT INTO CANDIDATOS_ELECCION (id_candidato, id_eleccion, numero_campania, cargo)
VALUES (4, 1, 1004, 'Presidente Consejo');

-- ELECCION_ROLES — pesos por rol en eleccion 1
INSERT INTO ELECCION_ROLES (id_eleccion, id_rol, peso_voto, fecha_configuracion)
VALUES (1, 1, 1.00, SYSDATE);   -- Estudiante
INSERT INTO ELECCION_ROLES (id_eleccion, id_rol, peso_voto, fecha_configuracion)
VALUES (1, 2, 2.00, SYSDATE);   -- Docente
INSERT INTO ELECCION_ROLES (id_eleccion, id_rol, peso_voto, fecha_configuracion)
VALUES (1, 3, 1.00, SYSDATE);   -- Egresado
INSERT INTO ELECCION_ROLES (id_eleccion, id_rol, peso_voto, fecha_configuracion)
VALUES (1, 4, 1.50, SYSDATE);   -- Administrativo

-- ADMINISTRADORES
INSERT INTO ADMINISTRADORES (id_admin, usuario, password_hash, nombre, correo)
VALUES (seq_administradores.NEXTVAL, 'admin_sistema',
        'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f',
        'Administrador Principal', 'admin@unicesar.edu.co');

-- VOTANTES
INSERT INTO VOTANTES (identificacion, correo, primer_nombre, primer_apellido,
                      estado_voto, fecha_consentimiento, id_rol, id_puesto)
VALUES ('1001001001','ana.cuellar@unicesar.edu.co','Ana','Cuellar',
        'PENDIENTE', SYSTIMESTAMP, 1, 1);

INSERT INTO VOTANTES (identificacion, correo, primer_nombre, primer_apellido,
                      estado_voto, fecha_consentimiento, id_rol, id_puesto)
VALUES ('1001001002','daniel.turizo@unicesar.edu.co','Daniel','Turizo',
        'PENDIENTE', SYSTIMESTAMP, 1, 1);

INSERT INTO VOTANTES (identificacion, correo, primer_nombre, primer_apellido,
                      estado_voto, fecha_consentimiento, id_rol, id_puesto)
VALUES ('1001001003','mateo.calderon@unicesar.edu.co','Mateo','Calderon',
        'PENDIENTE', SYSTIMESTAMP, 2, 1);

INSERT INTO VOTANTES (identificacion, correo, primer_nombre, primer_apellido,
                      estado_voto, fecha_consentimiento, id_rol, id_puesto)
VALUES ('1001001004','jorge.herrera@unicesar.edu.co','Jorge','Herrera',
        'PENDIENTE', SYSTIMESTAMP, 2, 2);

INSERT INTO VOTANTES (identificacion, correo, primer_nombre, primer_apellido,
                      estado_voto, fecha_consentimiento, id_rol, id_puesto)
VALUES ('1001001005','sofia.garcia@unicesar.edu.co','Sofia','Garcia',
        'PENDIENTE', SYSTIMESTAMP, 3, 2);

INSERT INTO VOTANTES (identificacion, correo, primer_nombre, primer_apellido,
                      estado_voto, fecha_consentimiento, id_rol, id_puesto)
VALUES ('1001001006','pedro.mora@unicesar.edu.co','Pedro','Mora',
        'PENDIENTE', SYSTIMESTAMP, 4, 3);

INSERT INTO VOTANTES (identificacion, correo, primer_nombre, primer_apellido,
                      estado_voto, fecha_consentimiento, id_rol, id_puesto)
VALUES ('1001001007','lucia.perez@unicesar.edu.co','Lucia','Perez',
        'INHABILITADO', SYSTIMESTAMP, 1, 3);

INSERT INTO VOTANTES (identificacion, correo, primer_nombre, primer_apellido,
                      estado_voto, fecha_consentimiento, id_rol, id_puesto)
VALUES ('1001001008','carlos.ruiz@unicesar.edu.co','Carlos','Ruiz',
        'PENDIENTE', SYSTIMESTAMP, 1, 1);

COMMIT;
```

---

## 3. Triggers

El sistema cuenta con **14 triggers activos** distribuidos en 8 tablas.

### 3.1 TRG_AUDITORIA_ESTADO_VOTO
**Tabla:** VOTANTES | **Evento:** AFTER UPDATE OF estado_voto  
**Propósito:** Registra automáticamente en AUDITORIA_VOTANTES cada cambio de estado del votante. Cumple trazabilidad completa exigida por el sistema electoral.

```sql
CREATE OR REPLACE TRIGGER trg_auditoria_estado_voto
AFTER UPDATE OF estado_voto ON VOTANTES
FOR EACH ROW
WHEN (OLD.estado_voto <> NEW.estado_voto)
DECLARE
    v_accion VARCHAR2(30);
BEGIN
    IF :NEW.estado_voto = 'EJERCIDO' THEN
        v_accion := 'RESET_ESTADO_VOTO';
    ELSIF :NEW.estado_voto = 'INHABILITADO' THEN
        v_accion := 'INHABILITACION';
    ELSIF :NEW.estado_voto = 'PENDIENTE' THEN
        v_accion := 'HABILITACION';
    ELSE
        v_accion := 'EDICION_DATOS';
    END IF;

    INSERT INTO AUDITORIA_VOTANTES (
        id_auditoria, identificacion, id_admin,
        campo_modificado, valor_anterior, valor_nuevo,
        motivo, accion, fecha_hora
    ) VALUES (
        seq_auditoria_votantes.NEXTVAL,
        :OLD.identificacion, 1,
        'estado_voto', :OLD.estado_voto, :NEW.estado_voto,
        'Cambio automatico registrado por trigger',
        v_accion, SYSTIMESTAMP
    );
END trg_auditoria_estado_voto;
/
```

### 3.2 TRG_NORMALIZAR_VOTANTE
**Tabla:** VOTANTES | **Evento:** BEFORE INSERT  
**Propósito:** Garantiza calidad del dato — normaliza nombres con INITCAP, correo en minúsculas, y fuerza estado_voto = 'PENDIENTE' sin importar qué envíe la aplicación.

```sql
CREATE OR REPLACE TRIGGER trg_normalizar_votante
BEFORE INSERT ON VOTANTES
FOR EACH ROW
BEGIN
    :NEW.primer_nombre    := INITCAP(TRIM(:NEW.primer_nombre));
    :NEW.segundo_nombre   := INITCAP(TRIM(:NEW.segundo_nombre));
    :NEW.primer_apellido  := INITCAP(TRIM(:NEW.primer_apellido));
    :NEW.segundo_apellido := INITCAP(TRIM(:NEW.segundo_apellido));
    :NEW.correo           := LOWER(TRIM(:NEW.correo));
    :NEW.estado_voto      := 'PENDIENTE';
END trg_normalizar_votante;
/
```

### 3.3 TRG_VALIDAR_ELECCION_ACTIVA
**Tabla:** VOTOS | **Evento:** BEFORE INSERT  
**Propósito:** Impide registrar votos en elecciones que no estén EN_CURSO. Protege la integridad electoral.

```sql
CREATE OR REPLACE TRIGGER trg_validar_eleccion_activa
BEFORE INSERT ON VOTOS
FOR EACH ROW
DECLARE
    v_estado ELECCIONES.estado%TYPE;
BEGIN
    SELECT estado INTO v_estado
    FROM ELECCIONES WHERE id_eleccion = :NEW.id_eleccion;

    IF v_estado <> 'EN_CURSO' THEN
        RAISE_APPLICATION_ERROR(-20001,
            'No se puede registrar el voto. La eleccion ' ||
            :NEW.id_eleccion || ' tiene estado: ' || v_estado ||
            '. Solo se permiten votos en elecciones EN_CURSO.');
    END IF;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20002,
            'La eleccion ' || :NEW.id_eleccion ||
            ' no existe en el sistema.');
END trg_validar_eleccion_activa;
/
```

### 3.4 TRG_VALIDAR_ACCION_AUDITORIA
**Tabla:** AUDITORIA_VOTANTES | **Evento:** BEFORE INSERT  
**Propósito:** Reemplaza el CHECK constraint estático con validación dinámica PL/SQL. Valida acción contra colección, verifica existencia del administrador, normaliza datos. Maneja 4 tipos de error personalizados.

```sql
CREATE OR REPLACE TRIGGER trg_validar_accion_auditoria
BEFORE INSERT ON AUDITORIA_VOTANTES
FOR EACH ROW
DECLARE
    TYPE t_acciones IS TABLE OF VARCHAR2(30);
    v_acciones_validas T_ACCIONES := T_ACCIONES(
        'EDICION_DATOS', 'CAMBIO_ROL', 'CAMBIO_PUESTO',
        'INHABILITACION', 'HABILITACION', 'RE_ENROLAMIENTO',
        'ANONIMIZACION', 'RESET_ESTADO_VOTO'
    );
    v_accion_upper  VARCHAR2(30);
    v_es_valida     BOOLEAN := FALSE;
    v_admin_existe  NUMBER;
BEGIN
    v_accion_upper := UPPER(TRIM(:NEW.accion));
    :NEW.accion    := v_accion_upper;

    FOR i IN 1..v_acciones_validas.COUNT LOOP
        IF v_acciones_validas(i) = v_accion_upper THEN
            v_es_valida := TRUE;
            EXIT;
        END IF;
    END LOOP;

    IF NOT v_es_valida THEN
        RAISE_APPLICATION_ERROR(-20010,
            'Accion invalida: "' || :NEW.accion || '". ' ||
            'Valores permitidos: EDICION_DATOS, CAMBIO_ROL, CAMBIO_PUESTO, ' ||
            'INHABILITACION, HABILITACION, RE_ENROLAMIENTO, ' ||
            'ANONIMIZACION, RESET_ESTADO_VOTO.');
    END IF;

    SELECT COUNT(*) INTO v_admin_existe
    FROM ADMINISTRADORES WHERE id_admin = :NEW.id_admin;

    IF v_admin_existe = 0 THEN
        RAISE_APPLICATION_ERROR(-20012,
            'No se puede auditar: el administrador con id ' ||
            :NEW.id_admin || ' no existe en el sistema.');
    END IF;

    IF TRIM(:NEW.campo_modificado) IS NULL THEN
        RAISE_APPLICATION_ERROR(-20013,
            'El campo_modificado no puede estar vacio.');
    END IF;

    :NEW.campo_modificado := LOWER(TRIM(:NEW.campo_modificado));
END trg_validar_accion_auditoria;
/
```

### 3.5 TRG_VALIDAR_PARTICIPACION
**Tabla:** REGISTRO_VOTOS | **Evento:** BEFORE INSERT  
**Propósito:** Garantiza el secreto del sufragio e integridad del proceso. Verifica que el votante no esté inhabilitado, no haya votado ya, y no tenga registro duplicado en la misma elección.

```sql
CREATE OR REPLACE TRIGGER trg_validar_participacion
BEFORE INSERT ON REGISTRO_VOTOS
FOR EACH ROW
DECLARE
    v_estado    VOTANTES.estado_voto%TYPE;
    v_nombre    VARCHAR2(100);
    v_ya_voto   NUMBER;
BEGIN
    SELECT estado_voto,
           primer_nombre || ' ' || primer_apellido
    INTO   v_estado, v_nombre
    FROM   VOTANTES
    WHERE  identificacion = :NEW.identificacion;

    IF v_estado = 'INHABILITADO' THEN
        RAISE_APPLICATION_ERROR(-20020,
            'Participacion rechazada. El votante ' ||
            v_nombre || ' (' || :NEW.identificacion ||
            ') se encuentra INHABILITADO en el sistema.');
    END IF;

    IF v_estado = 'EJERCIDO' THEN
        RAISE_APPLICATION_ERROR(-20021,
            'Participacion rechazada. El votante ' ||
            v_nombre || ' (' || :NEW.identificacion ||
            ') ya ejercio su voto en esta jornada.');
    END IF;

    SELECT COUNT(*) INTO v_ya_voto
    FROM   REGISTRO_VOTOS
    WHERE  identificacion = :NEW.identificacion
    AND    id_eleccion    = :NEW.id_eleccion;

    IF v_ya_voto > 0 THEN
        RAISE_APPLICATION_ERROR(-20022,
            'Participacion rechazada. Ya existe un registro de participacion ' ||
            'para el votante ' || :NEW.identificacion ||
            ' en la eleccion ' || :NEW.id_eleccion || '.');
    END IF;
END trg_validar_participacion;
/
```

### 3.6 TRG_ACTUALIZAR_ESTADO_VOTO
**Tabla:** REGISTRO_VOTOS | **Evento:** AFTER INSERT  
**Propósito:** Garantiza consistencia — cuando se inserta un registro de participación, el estado del votante se actualiza automáticamente a EJERCIDO sin depender de la aplicación.

```sql
CREATE OR REPLACE TRIGGER trg_actualizar_estado_voto
AFTER INSERT ON REGISTRO_VOTOS
FOR EACH ROW
BEGIN
    UPDATE VOTANTES
    SET    estado_voto = 'EJERCIDO'
    WHERE  identificacion = :NEW.identificacion
    AND    estado_voto    = 'PENDIENTE';
END trg_actualizar_estado_voto;
/
```

### 3.7 TRG_PROTEGER_REGISTRO_UPDATE
**Tabla:** REGISTRO_VOTOS | **Evento:** BEFORE UPDATE  
**Propósito:** Cumplimiento constitucional — Artículo 258. Un registro de participación no puede modificarse una vez emitido.

```sql
CREATE OR REPLACE TRIGGER trg_proteger_registro_update
BEFORE UPDATE ON REGISTRO_VOTOS
FOR EACH ROW
BEGIN
    RAISE_APPLICATION_ERROR(-20092,
        'El registro de participacion no puede modificarse ' ||
        'una vez emitido el voto.');
END trg_proteger_registro_update;
/
```

### 3.8 TRG_PROTEGER_REGISTRO_DELETE
**Tabla:** REGISTRO_VOTOS | **Evento:** BEFORE DELETE  
**Propósito:** Cumplimiento constitucional — Artículo 258. Un registro de participación no puede eliminarse.

```sql
CREATE OR REPLACE TRIGGER trg_proteger_registro_delete
BEFORE DELETE ON REGISTRO_VOTOS
FOR EACH ROW
BEGIN
    RAISE_APPLICATION_ERROR(-20093,
        'El registro de participacion no puede eliminarse ' ||
        'una vez emitido el voto.');
END trg_proteger_registro_delete;
/
```

### 3.9 TRG_AUDITORIA_BIOMETRIA
**Tabla:** BIOMETRIA_VOTANTES | **Evento:** AFTER INSERT OR UPDATE OR DELETE  
**Propósito:** Cumplimiento Ley 1581 de 2012 — datos biométricos son datos sensibles. Cualquier operación queda registrada en AUDITORIA_VOTANTES con trazabilidad completa.

```sql
CREATE OR REPLACE TRIGGER trg_auditoria_biometria
AFTER INSERT OR UPDATE OR DELETE ON BIOMETRIA_VOTANTES
FOR EACH ROW
DECLARE
    v_identificacion VARCHAR2(20);
    v_accion         VARCHAR2(30);
    v_campo          VARCHAR2(50);
    v_anterior       VARCHAR2(500);
    v_nuevo          VARCHAR2(500);
BEGIN
    IF DELETING THEN
        v_identificacion := :OLD.identificacion;
        v_accion         := 'RE_ENROLAMIENTO';
        v_campo          := 'plantilla_biometrica';
        v_anterior       := 'BLOB:activo=' || :OLD.activo ||
                            '|hash=' || :OLD.hashintegridadbiometrica;
        v_nuevo          := NULL;
    ELSIF INSERTING THEN
        v_identificacion := :NEW.identificacion;
        v_accion         := 'RE_ENROLAMIENTO';
        v_campo          := 'plantilla_biometrica';
        v_anterior       := NULL;
        v_nuevo          := 'BLOB:activo=' || :NEW.activo ||
                            '|hash=' || :NEW.hashintegridadbiometrica;
    ELSIF UPDATING THEN
        v_identificacion := :NEW.identificacion;
        v_accion         := 'RE_ENROLAMIENTO';
        v_campo          := 'activo_biometria';
        v_anterior       := 'activo=' || :OLD.activo;
        v_nuevo          := 'activo=' || :NEW.activo;
    END IF;

    INSERT INTO AUDITORIA_VOTANTES (
        id_auditoria, identificacion, id_admin,
        campo_modificado, valor_anterior, valor_nuevo,
        motivo, accion, fecha_hora
    ) VALUES (
        seq_auditoria_votantes.NEXTVAL,
        v_identificacion, 1, v_campo,
        v_anterior, v_nuevo,
        'Operacion sobre dato biometrico sensible - Ley 1581/2012',
        v_accion, SYSTIMESTAMP
    );
END trg_auditoria_biometria;
/
```

### 3.10 TRG_VALIDAR_PESO_VOTO
**Tabla:** ELECCION_ROLES | **Evento:** BEFORE INSERT OR UPDATE  
**Propósito:** Impide configurar o modificar pesos de voto en elecciones que ya están EN_CURSO o CERRADAS. Los votos emitidos usarían un peso diferente corrompiendo los resultados.

```sql
CREATE OR REPLACE TRIGGER trg_validar_peso_voto
BEFORE INSERT OR UPDATE ON ELECCION_ROLES
FOR EACH ROW
DECLARE
    v_estado ELECCIONES.estado%TYPE;
    v_nombre ELECCIONES.nombre%TYPE;
BEGIN
    SELECT estado, nombre INTO v_estado, v_nombre
    FROM ELECCIONES WHERE id_eleccion = :NEW.id_eleccion;

    IF v_estado = 'EN_CURSO' THEN
        RAISE_APPLICATION_ERROR(-20030,
            'No se puede modificar el peso del voto. La eleccion "' ||
            v_nombre || '" ya esta EN_CURSO. ' ||
            'Los votos emitidos usarian un peso diferente.');
    END IF;

    IF v_estado = 'CERRADA' THEN
        RAISE_APPLICATION_ERROR(-20031,
            'No se puede modificar el peso del voto. La eleccion "' ||
            v_nombre || '" ya esta CERRADA.');
    END IF;

    IF :NEW.peso_voto > 10 THEN
        RAISE_APPLICATION_ERROR(-20032,
            'El peso del voto ' || :NEW.peso_voto ||
            ' excede el maximo permitido de 10.00.');
    END IF;
END trg_validar_peso_voto;
/
```

### 3.11 TRG_CANDIDATO_UNICO_ELECCION
**Tabla:** CANDIDATOS_ELECCION | **Evento:** BEFORE INSERT  
**Propósito:** Protege la integridad electoral — impide inscribir candidatos en elecciones activas, bloquea número de tarjetón duplicado, y evita que el mismo candidato se inscriba dos veces en la misma elección.

```sql
CREATE OR REPLACE TRIGGER trg_candidato_unico_eleccion
BEFORE INSERT ON CANDIDATOS_ELECCION
FOR EACH ROW
DECLARE
    v_numero_existe    NUMBER;
    v_candidato_existe NUMBER;
    v_nombre_candidato VARCHAR2(100);
    v_estado_eleccion  ELECCIONES.estado%TYPE;
    v_nombre_eleccion  ELECCIONES.nombre%TYPE;
BEGIN
    SELECT estado, nombre INTO v_estado_eleccion, v_nombre_eleccion
    FROM ELECCIONES WHERE id_eleccion = :NEW.id_eleccion;

    IF v_estado_eleccion IN ('EN_CURSO', 'CERRADA') THEN
        RAISE_APPLICATION_ERROR(-20050,
            'No se puede inscribir candidatos. La eleccion "' ||
            v_nombre_eleccion || '" ya esta ' || v_estado_eleccion || '.');
    END IF;

    SELECT COUNT(*) INTO v_numero_existe
    FROM CANDIDATOS_ELECCION
    WHERE id_eleccion = :NEW.id_eleccion
    AND numero_campania = :NEW.numero_campania;

    IF v_numero_existe > 0 THEN
        RAISE_APPLICATION_ERROR(-20051,
            'El numero de campania ' || :NEW.numero_campania ||
            ' ya esta asignado a otro candidato en la eleccion "' ||
            v_nombre_eleccion || '".');
    END IF;

    SELECT COUNT(*) INTO v_candidato_existe
    FROM CANDIDATOS_ELECCION
    WHERE id_eleccion = :NEW.id_eleccion
    AND id_candidato = :NEW.id_candidato;

    IF v_candidato_existe > 0 THEN
        SELECT primer_nombre || ' ' || primer_apellido
        INTO v_nombre_candidato
        FROM CANDIDATOS WHERE id_candidato = :NEW.id_candidato;

        RAISE_APPLICATION_ERROR(-20052,
            'El candidato "' || v_nombre_candidato ||
            '" ya esta inscrito en la eleccion "' ||
            v_nombre_eleccion || '".');
    END IF;

    :NEW.cargo := INITCAP(TRIM(:NEW.cargo));
END trg_candidato_unico_eleccion;
/
```

### 3.12 TRG_JURADO_NO_CANDIDATO
**Tabla:** JURADOS | **Evento:** BEFORE INSERT  
**Propósito:** Evita conflicto de interés electoral — un candidato activo no puede ser jurado. También bloquea votantes inhabilitados.

```sql
CREATE OR REPLACE TRIGGER trg_jurado_no_candidato
BEFORE INSERT ON JURADOS
FOR EACH ROW
DECLARE
    v_es_candidato  NUMBER;
    v_nombre_jurado VARCHAR2(100);
    v_estado        VOTANTES.estado_voto%TYPE;
BEGIN
    SELECT primer_nombre || ' ' || primer_apellido, estado_voto
    INTO   v_nombre_jurado, v_estado
    FROM   VOTANTES
    WHERE  identificacion = :NEW.votantes_identificacion;

    IF v_estado = 'INHABILITADO' THEN
        RAISE_APPLICATION_ERROR(-20061,
            'El votante "' || v_nombre_jurado ||
            '" esta INHABILITADO y no puede ser asignado como jurado.');
    END IF;

    SELECT COUNT(*) INTO v_es_candidato
    FROM   CANDIDATOS_ELECCION ce
    JOIN   ELECCIONES e  ON ce.id_eleccion  = e.id_eleccion
    JOIN   CANDIDATOS  c  ON ce.id_candidato = c.id_candidato
    JOIN   VOTANTES    v  ON v.primer_nombre  = c.primer_nombre
                         AND v.primer_apellido = c.primer_apellido
    WHERE  v.identificacion = :NEW.votantes_identificacion
    AND    e.estado IN ('PROGRAMADA', 'EN_CURSO');

    IF v_es_candidato > 0 THEN
        RAISE_APPLICATION_ERROR(-20060,
            'Conflicto de interes: "' || v_nombre_jurado ||
            '" esta inscrito como candidato en una eleccion activa ' ||
            'y no puede ser asignado como jurado.');
    END IF;
END trg_jurado_no_candidato;
/
```

### 3.13 TRG_PROTEGER_VOTOS_UPDATE
**Tabla:** VOTOS | **Evento:** BEFORE UPDATE  
**Propósito:** Cumplimiento Artículo 258 Constitución Política de Colombia — los votos emitidos son inmutables.

```sql
CREATE OR REPLACE TRIGGER trg_proteger_votos_update
BEFORE UPDATE ON VOTOS
FOR EACH ROW
BEGIN
    RAISE_APPLICATION_ERROR(-20090,
        'Los votos emitidos no pueden modificarse. ' ||
        'Articulo 258 Constitucion Politica de Colombia.');
END trg_proteger_votos_update;
/
```

### 3.14 TRG_PROTEGER_VOTOS_DELETE
**Tabla:** VOTOS | **Evento:** BEFORE DELETE  
**Propósito:** Cumplimiento Artículo 258 Constitución Política de Colombia — los votos emitidos no pueden eliminarse.

```sql
CREATE OR REPLACE TRIGGER trg_proteger_votos_delete
BEFORE DELETE ON VOTOS
FOR EACH ROW
BEGIN
    RAISE_APPLICATION_ERROR(-20091,
        'Los votos emitidos no pueden eliminarse. ' ||
        'Articulo 258 Constitucion Politica de Colombia.');
END trg_proteger_votos_delete;
/
```

---

## 4. Funciones almacenadas

### 4.1 FNC_PESO_VOTO_VOTANTE
**Retorna:** NUMBER  
**Propósito:** Obtiene el peso del voto de un votante en una elección específica según su rol. Usada internamente por `prc_registrar_voto`.

```sql
CREATE OR REPLACE FUNCTION fnc_peso_voto_votante(
    p_identificacion IN VARCHAR2,
    p_id_eleccion    IN NUMBER
) RETURN NUMBER AS
    v_id_rol NUMBER;
    v_peso   ELECCION_ROLES.peso_voto%TYPE;
BEGIN
    SELECT id_rol INTO v_id_rol
    FROM VOTANTES WHERE identificacion = p_identificacion;

    SELECT peso_voto INTO v_peso
    FROM ELECCION_ROLES
    WHERE id_eleccion = p_id_eleccion AND id_rol = v_id_rol;

    RETURN v_peso;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20080,
            'No se encontro configuracion de peso para el votante ' ||
            p_identificacion || ' en la eleccion ' || p_id_eleccion ||
            '. Verifique que el rol este configurado en ELECCION_ROLES.');
    WHEN OTHERS THEN
        RAISE_APPLICATION_ERROR(-20081,
            'Error al calcular peso del voto: ' || SQLERRM);
END fnc_peso_voto_votante;
/
```

**Uso desde backend:**
```sql
SELECT fnc_peso_voto_votante('1001001001', 1) FROM dual;
```

### 4.2 FNC_VOTANTE_PUEDE_VOTAR
**Retorna:** VARCHAR2 ('S' o 'N')  
**Parámetro OUT:** p_motivo VARCHAR2 — mensaje para mostrar al usuario  
**Propósito:** Validación completa antes de mostrar pantalla de votación. Verifica existencia, estado, estado de elección y registro previo.

```sql
CREATE OR REPLACE FUNCTION fnc_votante_puede_votar(
    p_identificacion IN  VARCHAR2,
    p_id_eleccion    IN  NUMBER,
    p_motivo         OUT VARCHAR2
) RETURN VARCHAR2 AS
    v_estado_votante  VOTANTES.estado_voto%TYPE;
    v_estado_eleccion ELECCIONES.estado%TYPE;
    v_nombre          VARCHAR2(100);
    v_ya_registro     NUMBER;
BEGIN
    BEGIN
        SELECT estado_voto, primer_nombre || ' ' || primer_apellido
        INTO   v_estado_votante, v_nombre
        FROM   VOTANTES WHERE identificacion = p_identificacion;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_motivo := 'El votante ' || p_identificacion ||
                        ' no esta registrado en el sistema.';
            RETURN 'N';
    END;

    IF v_estado_votante = 'INHABILITADO' THEN
        p_motivo := v_nombre || ' esta INHABILITADO. ' ||
                    'Contacte al administrador del sistema.';
        RETURN 'N';
    END IF;

    IF v_estado_votante = 'EJERCIDO' THEN
        p_motivo := v_nombre || ' ya ejercio su voto en esta jornada.';
        RETURN 'N';
    END IF;

    BEGIN
        SELECT estado INTO v_estado_eleccion
        FROM ELECCIONES WHERE id_eleccion = p_id_eleccion;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_motivo := 'La eleccion ' || p_id_eleccion ||
                        ' no existe en el sistema.';
            RETURN 'N';
    END;

    IF v_estado_eleccion = 'PROGRAMADA' THEN
        p_motivo := 'La eleccion aun no ha iniciado.';
        RETURN 'N';
    END IF;

    IF v_estado_eleccion = 'CERRADA' THEN
        p_motivo := 'La eleccion ya fue cerrada. No se aceptan mas votos.';
        RETURN 'N';
    END IF;

    SELECT COUNT(*) INTO v_ya_registro
    FROM REGISTRO_VOTOS
    WHERE identificacion = p_identificacion AND id_eleccion = p_id_eleccion;

    IF v_ya_registro > 0 THEN
        p_motivo := v_nombre || ' ya tiene registro de participacion ' ||
                    'en esta eleccion.';
        RETURN 'N';
    END IF;

    p_motivo := 'Bienvenido, ' || v_nombre || '. Puede proceder a votar.';
    RETURN 'S';
EXCEPTION
    WHEN OTHERS THEN
        p_motivo := 'Error inesperado: ' || SQLERRM;
        RETURN 'N';
END fnc_votante_puede_votar;
/
```

**Uso desde backend:**
```sql
DECLARE
    v_puede  VARCHAR2(1);
    v_motivo VARCHAR2(500);
BEGIN
    v_puede := fnc_votante_puede_votar('1001001001', 1, v_motivo);
    -- v_puede = 'S' o 'N'
    -- v_motivo = mensaje para mostrar al usuario
END;
```

### 4.3 FNC_CALCULAR_VOTOS_CANDIDATO
**Retorna:** NUMBER (total ponderado)  
**Propósito:** Suma los votos ponderados de un candidato en una elección. Base del sistema de resultados.

```sql
CREATE OR REPLACE FUNCTION fnc_calcular_votos_candidato(
    p_id_candidato IN NUMBER,
    p_id_eleccion  IN NUMBER
) RETURN NUMBER AS
    v_total            NUMBER;
    v_candidato_existe NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_candidato_existe
    FROM CANDIDATOS_ELECCION
    WHERE id_candidato = p_id_candidato AND id_eleccion = p_id_eleccion;

    IF v_candidato_existe = 0 THEN
        RAISE_APPLICATION_ERROR(-20082,
            'El candidato ' || p_id_candidato ||
            ' no esta inscrito en la eleccion ' || p_id_eleccion || '.');
    END IF;

    SELECT NVL(SUM(pesovoto_aplicado), 0) INTO v_total
    FROM VOTOS
    WHERE id_candidato = p_id_candidato AND id_eleccion = p_id_eleccion;

    RETURN v_total;
EXCEPTION
    WHEN OTHERS THEN
        RAISE_APPLICATION_ERROR(-20083,
            'Error al calcular votos: ' || SQLERRM);
END fnc_calcular_votos_candidato;
/
```

**Uso desde backend — tabla de resultados:**
```sql
SELECT c.primer_nombre || ' ' || c.primer_apellido nombre,
       ce.numero_campania tarjeton,
       fnc_calcular_votos_candidato(c.id_candidato, 1) votos_ponderados
FROM   CANDIDATOS_ELECCION ce
JOIN   CANDIDATOS c ON ce.id_candidato = c.id_candidato
WHERE  ce.id_eleccion = 1
ORDER BY votos_ponderados DESC;
```

### 4.4 FNC_PORCENTAJE_PARTICIPACION
**Retorna:** NUMBER (porcentaje con 2 decimales)  
**Propósito:** Panel de control en tiempo real — porcentaje de votantes que ya participaron sobre el total habilitado.

```sql
CREATE OR REPLACE FUNCTION fnc_porcentaje_participacion(
    p_id_eleccion IN NUMBER
) RETURN NUMBER AS
    v_total_habilitados NUMBER;
    v_total_ejercidos   NUMBER;
    v_porcentaje        NUMBER;
    v_eleccion_existe   NUMBER;
    v_nombre_eleccion   ELECCIONES.nombre%TYPE;
BEGIN
    SELECT COUNT(*), MAX(nombre)
    INTO   v_eleccion_existe, v_nombre_eleccion
    FROM ELECCIONES WHERE id_eleccion = p_id_eleccion;

    IF v_eleccion_existe = 0 THEN
        RAISE_APPLICATION_ERROR(-20084,
            'La eleccion ' || p_id_eleccion || ' no existe.');
    END IF;

    SELECT COUNT(*) INTO v_total_habilitados
    FROM VOTANTES WHERE estado_voto IN ('PENDIENTE', 'EJERCIDO');

    SELECT COUNT(*) INTO v_total_ejercidos
    FROM REGISTRO_VOTOS WHERE id_eleccion = p_id_eleccion;

    IF v_total_habilitados = 0 THEN RETURN 0; END IF;

    v_porcentaje := ROUND((v_total_ejercidos / v_total_habilitados) * 100, 2);
    RETURN v_porcentaje;
EXCEPTION
    WHEN OTHERS THEN
        RAISE_APPLICATION_ERROR(-20085,
            'Error al calcular participacion: ' || SQLERRM);
END fnc_porcentaje_participacion;
/
```

**Uso desde backend — panel de control:**
```sql
SELECT e.nombre eleccion, e.estado,
       fnc_porcentaje_participacion(e.id_eleccion) porcentaje
FROM ELECCIONES e WHERE e.estado = 'EN_CURSO';
```

---

## 5. Procedimientos almacenados

### 5.1 PRC_REGISTRAR_VOTO
**Propósito:** Operación electoral más crítica. Transacción atómica que registra participación en REGISTRO_VOTOS, inserta el voto anónimo en VOTOS, y actualiza estado del votante. Si cualquier paso falla, ROLLBACK total.

```sql
CREATE OR REPLACE PROCEDURE prc_registrar_voto(
    p_identificacion IN VARCHAR2,
    p_id_eleccion    IN NUMBER,
    p_id_candidato   IN NUMBER,  -- NULL = voto en blanco
    p_id_puesto      IN NUMBER
) AS
    v_estado          VOTANTES.estado_voto%TYPE;
    v_peso            ELECCION_ROLES.peso_voto%TYPE;
    v_nombre          VARCHAR2(100);
    v_estado_eleccion ELECCIONES.estado%TYPE;
    v_puede           VARCHAR2(1);
    v_motivo          VARCHAR2(500);
BEGIN
    v_puede := fnc_votante_puede_votar(p_identificacion, p_id_eleccion, v_motivo);

    IF v_puede = 'N' THEN
        RAISE_APPLICATION_ERROR(-20070, v_motivo);
    END IF;

    v_peso := fnc_peso_voto_votante(p_identificacion, p_id_eleccion);

    INSERT INTO REGISTRO_VOTOS (
        id_registro, fecha_hora, identificacion, id_puesto, id_eleccion)
    VALUES (
        seq_registro_votos.NEXTVAL, SYSTIMESTAMP,
        p_identificacion, p_id_puesto, p_id_eleccion);

    INSERT INTO VOTOS (
        id_votos, id_eleccion, id_candidato, fecha_hora, pesovoto_aplicado)
    VALUES (
        seq_votos.NEXTVAL, p_id_eleccion,
        p_id_candidato, SYSTIMESTAMP, v_peso);

    COMMIT;

    SELECT primer_nombre || ' ' || primer_apellido
    INTO v_nombre FROM VOTANTES WHERE identificacion = p_identificacion;

    DBMS_OUTPUT.PUT_LINE('VOTO REGISTRADO EXITOSAMENTE');
    DBMS_OUTPUT.PUT_LINE('Votante      : ' || v_nombre);
    DBMS_OUTPUT.PUT_LINE('Peso aplicado: ' || v_peso);
    IF p_id_candidato IS NULL THEN
        DBMS_OUTPUT.PUT_LINE('Candidato    : VOTO EN BLANCO');
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('ERROR: ' || SQLERRM);
        RAISE;
END prc_registrar_voto;
/
```

**Llamada desde backend:**
```sql
-- Voto normal
EXEC prc_registrar_voto('1001001001', 1, 2, 1);

-- Voto en blanco
EXEC prc_registrar_voto('1001001001', 1, NULL, 1);
```

### 5.2 PRC_INHABILITAR_VOTANTE
**Propósito:** Inhabilita un votante con motivo obligatorio. No permite inhabilitar a quien ya votó.

```sql
CREATE OR REPLACE PROCEDURE prc_inhabilitar_votante(
    p_identificacion IN VARCHAR2,
    p_id_admin       IN NUMBER,
    p_motivo         IN VARCHAR2
) AS
    v_estado VARCHAR2(15);
    v_nombre VARCHAR2(100);
BEGIN
    BEGIN
        SELECT estado_voto, primer_nombre || ' ' || primer_apellido
        INTO v_estado, v_nombre
        FROM VOTANTES WHERE identificacion = p_identificacion;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20100,
                'El votante ' || p_identificacion || ' no existe.');
    END;

    IF TRIM(p_motivo) IS NULL THEN
        RAISE_APPLICATION_ERROR(-20101,
            'El motivo de inhabilitacion es obligatorio.');
    END IF;

    IF v_estado = 'INHABILITADO' THEN
        RAISE_APPLICATION_ERROR(-20102,
            'El votante "' || v_nombre || '" ya esta INHABILITADO.');
    END IF;

    IF v_estado = 'EJERCIDO' THEN
        RAISE_APPLICATION_ERROR(-20103,
            'El votante "' || v_nombre ||
            '" ya ejercio su voto. No se puede inhabilitar.');
    END IF;

    UPDATE VOTANTES SET estado_voto = 'INHABILITADO'
    WHERE identificacion = p_identificacion;

    UPDATE AUDITORIA_VOTANTES
    SET motivo = p_motivo, id_admin = p_id_admin
    WHERE id_auditoria = (
        SELECT MAX(id_auditoria) FROM AUDITORIA_VOTANTES
        WHERE identificacion = p_identificacion AND accion = 'INHABILITACION');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Votante ' || v_nombre || ' inhabilitado.');
EXCEPTION
    WHEN OTHERS THEN ROLLBACK;
    DBMS_OUTPUT.PUT_LINE('ERROR: ' || SQLERRM); RAISE;
END prc_inhabilitar_votante;
/
```

### 5.3 PRC_HABILITAR_VOTANTE
**Propósito:** Reactiva un votante INHABILITADO con motivo obligatorio.

```sql
CREATE OR REPLACE PROCEDURE prc_habilitar_votante(
    p_identificacion IN VARCHAR2,
    p_id_admin       IN NUMBER,
    p_motivo         IN VARCHAR2
) AS
    v_estado VARCHAR2(15);
    v_nombre VARCHAR2(100);
BEGIN
    BEGIN
        SELECT estado_voto, primer_nombre || ' ' || primer_apellido
        INTO v_estado, v_nombre
        FROM VOTANTES WHERE identificacion = p_identificacion;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20110,
                'El votante ' || p_identificacion || ' no existe.');
    END;

    IF TRIM(p_motivo) IS NULL THEN
        RAISE_APPLICATION_ERROR(-20111,
            'El motivo de habilitacion es obligatorio.');
    END IF;

    IF v_estado = 'PENDIENTE' THEN
        RAISE_APPLICATION_ERROR(-20112,
            'El votante "' || v_nombre || '" ya esta PENDIENTE.');
    END IF;

    IF v_estado = 'EJERCIDO' THEN
        RAISE_APPLICATION_ERROR(-20113,
            'El votante "' || v_nombre ||
            '" ya ejercio su voto. No puede modificarse su estado.');
    END IF;

    UPDATE VOTANTES SET estado_voto = 'PENDIENTE'
    WHERE identificacion = p_identificacion;

    UPDATE AUDITORIA_VOTANTES
    SET motivo = p_motivo, id_admin = p_id_admin
    WHERE id_auditoria = (
        SELECT MAX(id_auditoria) FROM AUDITORIA_VOTANTES
        WHERE identificacion = p_identificacion AND accion = 'HABILITACION');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Votante ' || v_nombre || ' habilitado.');
EXCEPTION
    WHEN OTHERS THEN ROLLBACK;
    DBMS_OUTPUT.PUT_LINE('ERROR: ' || SQLERRM); RAISE;
END prc_habilitar_votante;
/
```

### 5.4 PRC_CERRAR_ELECCION
**Propósito:** Cierra oficialmente una elección EN_CURSO. Muestra resumen de participación y resultados ponderados finales.

```sql
CREATE OR REPLACE PROCEDURE prc_cerrar_eleccion(
    p_id_eleccion IN NUMBER,
    p_id_admin    IN NUMBER
) AS
    v_estado        ELECCIONES.estado%TYPE;
    v_nombre        ELECCIONES.nombre%TYPE;
    v_participacion NUMBER;
    v_total_votos   NUMBER;
    v_admin_existe  NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_admin_existe
    FROM ADMINISTRADORES WHERE id_admin = p_id_admin;

    IF v_admin_existe = 0 THEN
        RAISE_APPLICATION_ERROR(-20120,
            'El administrador ' || p_id_admin || ' no existe.');
    END IF;

    BEGIN
        SELECT estado, nombre INTO v_estado, v_nombre
        FROM ELECCIONES WHERE id_eleccion = p_id_eleccion;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20121,
                'La eleccion ' || p_id_eleccion || ' no existe.');
    END;

    IF v_estado = 'CERRADA' THEN
        RAISE_APPLICATION_ERROR(-20122,
            'La eleccion "' || v_nombre || '" ya esta CERRADA.');
    END IF;

    IF v_estado = 'PROGRAMADA' THEN
        RAISE_APPLICATION_ERROR(-20123,
            'La eleccion "' || v_nombre || '" nunca estuvo EN_CURSO.');
    END IF;

    v_participacion := fnc_porcentaje_participacion(p_id_eleccion);
    SELECT COUNT(*) INTO v_total_votos FROM VOTOS WHERE id_eleccion = p_id_eleccion;

    UPDATE ELECCIONES SET estado = 'CERRADA' WHERE id_eleccion = p_id_eleccion;
    COMMIT;

    DBMS_OUTPUT.PUT_LINE('ELECCION CERRADA: ' || v_nombre);
    DBMS_OUTPUT.PUT_LINE('Participacion : ' || v_participacion || '%');
    DBMS_OUTPUT.PUT_LINE('Total votos   : ' || v_total_votos);

    FOR r IN (
        SELECT c.primer_nombre || ' ' || c.primer_apellido nombre,
               ce.numero_campania tarjeton,
               fnc_calcular_votos_candidato(c.id_candidato, p_id_eleccion) votos
        FROM CANDIDATOS_ELECCION ce
        JOIN CANDIDATOS c ON ce.id_candidato = c.id_candidato
        WHERE ce.id_eleccion = p_id_eleccion
        ORDER BY fnc_calcular_votos_candidato(c.id_candidato, p_id_eleccion) DESC
    ) LOOP
        DBMS_OUTPUT.PUT_LINE(RPAD(r.nombre,25) ||
            ' T:' || r.tarjeton || ' V:' || r.votos);
    END LOOP;
EXCEPTION
    WHEN OTHERS THEN ROLLBACK;
    DBMS_OUTPUT.PUT_LINE('ERROR: ' || SQLERRM); RAISE;
END prc_cerrar_eleccion;
/
```

### 5.5 PRC_ENROLAR_BIOMETRIA
**Propósito:** Registra o actualiza la huella dactilar. Si existe biometría activa la desactiva primero conservando el historial. Valida hash SHA-256 de 64 caracteres.

```sql
CREATE OR REPLACE PROCEDURE prc_enrolar_biometria(
    p_identificacion       IN VARCHAR2,
    p_plantilla_biometrica IN BLOB,
    p_hash_integridad      IN VARCHAR2
) AS
    v_nombre        VARCHAR2(100);
    v_estado        VOTANTES.estado_voto%TYPE;
    v_existe_activa NUMBER;
    v_nuevo_id      NUMBER;
BEGIN
    BEGIN
        SELECT primer_nombre || ' ' || primer_apellido, estado_voto
        INTO v_nombre, v_estado
        FROM VOTANTES WHERE identificacion = p_identificacion;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20130,
                'El votante ' || p_identificacion || ' no existe.');
    END;

    IF v_estado = 'INHABILITADO' THEN
        RAISE_APPLICATION_ERROR(-20131,
            'El votante "' || v_nombre || '" esta INHABILITADO.');
    END IF;

    IF TRIM(p_hash_integridad) IS NULL THEN
        RAISE_APPLICATION_ERROR(-20132,
            'El hash de integridad es obligatorio.');
    END IF;

    IF LENGTH(TRIM(p_hash_integridad)) <> 64 THEN
        RAISE_APPLICATION_ERROR(-20133,
            'El hash debe ser SHA-256 (64 caracteres). ' ||
            'Se recibieron: ' || LENGTH(TRIM(p_hash_integridad)));
    END IF;

    SELECT COUNT(*) INTO v_existe_activa
    FROM BIOMETRIA_VOTANTES
    WHERE identificacion = p_identificacion AND activo = 'S';

    IF v_existe_activa > 0 THEN
        UPDATE BIOMETRIA_VOTANTES SET activo = 'N'
        WHERE identificacion = p_identificacion AND activo = 'S';
    END IF;

    SELECT seq_biometria_votantes.NEXTVAL INTO v_nuevo_id FROM dual;

    INSERT INTO BIOMETRIA_VOTANTES (
        id_biometria, identificacion, plantilla_biometrica,
        hashintegridadbiometrica, fecha_enrolamiento, activo)
    VALUES (v_nuevo_id, p_identificacion, p_plantilla_biometrica,
            TRIM(p_hash_integridad), SYSDATE, 'S');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Enrolamiento exitoso. Votante: ' || v_nombre);
    DBMS_OUTPUT.PUT_LINE('ID biometria: ' || v_nuevo_id);
EXCEPTION
    WHEN OTHERS THEN ROLLBACK;
    DBMS_OUTPUT.PUT_LINE('ERROR: ' || SQLERRM); RAISE;
END prc_enrolar_biometria;
/
```

### 5.6 PRC_ASIGNAR_JURADO
**Propósito:** Asigna un votante como jurado en una mesa de su mismo puesto de votación. El trigger `trg_jurado_no_candidato` actúa automáticamente.

```sql
CREATE OR REPLACE PROCEDURE prc_asignar_jurado(
    p_id_mesa        IN NUMBER,
    p_identificacion IN VARCHAR2,
    p_cargo          IN VARCHAR2
) AS
    v_nombre         VARCHAR2(100);
    v_estado         VOTANTES.estado_voto%TYPE;
    v_mesa_existe    NUMBER;
    v_ya_es_jurado   NUMBER;
    v_id_puesto_mesa NUMBER;
    v_id_puesto_vot  NUMBER;
BEGIN
    SELECT COUNT(*), MAX(puestos_votacion_idpuestos)
    INTO v_mesa_existe, v_id_puesto_mesa
    FROM MESA_JURADOS WHERE id_mesa = p_id_mesa;

    IF v_mesa_existe = 0 THEN
        RAISE_APPLICATION_ERROR(-20140,
            'La mesa ' || p_id_mesa || ' no existe.');
    END IF;

    BEGIN
        SELECT primer_nombre || ' ' || primer_apellido,
               estado_voto, id_puesto
        INTO v_nombre, v_estado, v_id_puesto_vot
        FROM VOTANTES WHERE identificacion = p_identificacion;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20141,
                'El votante ' || p_identificacion || ' no existe.');
    END;

    IF TRIM(p_cargo) IS NULL THEN
        RAISE_APPLICATION_ERROR(-20142,
            'El cargo del jurado es obligatorio.');
    END IF;

    IF v_id_puesto_vot <> v_id_puesto_mesa THEN
        RAISE_APPLICATION_ERROR(-20143,
            'El votante "' || v_nombre ||
            '" pertenece al puesto ' || v_id_puesto_vot ||
            ' y la mesa ' || p_id_mesa ||
            ' pertenece al puesto ' || v_id_puesto_mesa ||
            '. Un jurado debe operar en su propio puesto.');
    END IF;

    SELECT COUNT(*) INTO v_ya_es_jurado
    FROM JURADOS
    WHERE mesa_jurados_idmesa = p_id_mesa
    AND votantes_identificacion = p_identificacion;

    IF v_ya_es_jurado > 0 THEN
        RAISE_APPLICATION_ERROR(-20144,
            'El votante "' || v_nombre ||
            '" ya esta asignado en la mesa ' || p_id_mesa || '.');
    END IF;

    INSERT INTO JURADOS (
        mesa_jurados_idmesa, votantes_identificacion,
        fecha_asignacion, cargo)
    VALUES (p_id_mesa, p_identificacion, SYSDATE, INITCAP(TRIM(p_cargo)));

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Jurado asignado: ' || v_nombre ||
                         ' | Mesa: ' || p_id_mesa ||
                         ' | Cargo: ' || INITCAP(TRIM(p_cargo)));
EXCEPTION
    WHEN OTHERS THEN ROLLBACK;
    DBMS_OUTPUT.PUT_LINE('ERROR: ' || SQLERRM); RAISE;
END prc_asignar_jurado;
/
```

### 5.7 PRC_ELIMINAR_VOTANTE_PRUEBA
**Propósito:** Utilitario para etapa de desarrollo. Elimina un votante y todos sus registros dependientes en el orden correcto respetando las FK.

```sql
CREATE OR REPLACE PROCEDURE prc_eliminar_votante_prueba(
    p_identificacion IN VARCHAR2
) AS
    v_nombre VARCHAR2(100);
    v_existe NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_existe
    FROM VOTANTES WHERE identificacion = p_identificacion;

    IF v_existe = 0 THEN
        RAISE_APPLICATION_ERROR(-20090,
            'El votante ' || p_identificacion || ' no existe.');
    END IF;

    SELECT primer_nombre || ' ' || primer_apellido INTO v_nombre
    FROM VOTANTES WHERE identificacion = p_identificacion;

    DELETE FROM BIOMETRIA_VOTANTES WHERE identificacion = p_identificacion;
    DELETE FROM JURADOS WHERE votantes_identificacion = p_identificacion;
    DELETE FROM AUDITORIA_VOTANTES WHERE identificacion = p_identificacion;
    DELETE FROM VOTANTES WHERE identificacion = p_identificacion;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Votante ' || v_nombre || ' eliminado.');
EXCEPTION
    WHEN OTHERS THEN ROLLBACK;
    DBMS_OUTPUT.PUT_LINE('ERROR: ' || SQLERRM); RAISE;
END prc_eliminar_votante_prueba;
/
```

> **Nota:** No elimina REGISTRO_VOTOS ni VOTOS porque están protegidos por triggers constitucionales. Solo usar en entorno de desarrollo con votantes que no hayan votado.

---

## 6. Referencia para integración con backend

### 6.1 Flujo completo de votación

```
1. Autenticación biométrica (lógica en aplicación)
2. Llamar fnc_votante_puede_votar() → verificar 'S'/'N' y mostrar motivo
3. Mostrar candidatos de la elección
4. Confirmar selección del votante
5. Llamar prc_registrar_voto() con id_candidato (NULL = voto en blanco)
6. Enviar correo de confirmación (lógica en aplicación, independiente del voto)
```

### 6.2 Consultas útiles para el backend

```sql
-- Panel de control en tiempo real
SELECT e.id_eleccion, e.nombre, e.estado,
       fnc_porcentaje_participacion(e.id_eleccion) participacion_pct,
       (SELECT COUNT(*) FROM REGISTRO_VOTOS rv
        WHERE rv.id_eleccion = e.id_eleccion) votantes_participaron
FROM ELECCIONES e WHERE e.estado = 'EN_CURSO';

-- Resultados de una elección
SELECT c.primer_nombre || ' ' || c.primer_apellido nombre,
       ce.numero_campania tarjeton, ce.cargo,
       fnc_calcular_votos_candidato(c.id_candidato, :id_eleccion) votos_ponderados
FROM CANDIDATOS_ELECCION ce
JOIN CANDIDATOS c ON ce.id_candidato = c.id_candidato
WHERE ce.id_eleccion = :id_eleccion
ORDER BY votos_ponderados DESC;

-- Verificar si votante puede votar (antes de mostrar pantalla)
DECLARE
    v_puede VARCHAR2(1);
    v_motivo VARCHAR2(500);
BEGIN
    v_puede := fnc_votante_puede_votar(:identificacion, :id_eleccion, v_motivo);
    -- Retornar v_puede y v_motivo a la aplicación
END;

-- Historial de auditoría de un votante
SELECT campo_modificado, valor_anterior, valor_nuevo,
       accion, motivo, fecha_hora
FROM AUDITORIA_VOTANTES
WHERE identificacion = :identificacion
ORDER BY fecha_hora DESC;

-- Biometría activa de un votante
SELECT id_biometria, plantilla_biometrica,
       hashintegridadbiometrica, fecha_enrolamiento
FROM BIOMETRIA_VOTANTES
WHERE identificacion = :identificacion AND activo = 'S';
```

### 6.3 Códigos de error personalizados

| Código | Origen | Descripción |
|--------|--------|-------------|
| ORA-20001 | trg_validar_eleccion_activa | Voto en elección no EN_CURSO |
| ORA-20002 | trg_validar_eleccion_activa | Elección no existe |
| ORA-20010 | trg_validar_accion_auditoria | Acción de auditoría inválida |
| ORA-20012 | trg_validar_accion_auditoria | Administrador no existe |
| ORA-20013 | trg_validar_accion_auditoria | campo_modificado vacío |
| ORA-20020 | trg_validar_participacion | Votante INHABILITADO |
| ORA-20021 | trg_validar_participacion | Votante ya votó |
| ORA-20022 | trg_validar_participacion | Registro duplicado |
| ORA-20030 | trg_validar_peso_voto | Elección EN_CURSO — no modificar peso |
| ORA-20031 | trg_validar_peso_voto | Elección CERRADA |
| ORA-20032 | trg_validar_peso_voto | Peso excede máximo de 10 |
| ORA-20050 | trg_candidato_unico_eleccion | Elección no PROGRAMADA |
| ORA-20051 | trg_candidato_unico_eleccion | Número tarjetón duplicado |
| ORA-20052 | trg_candidato_unico_eleccion | Candidato ya inscrito |
| ORA-20060 | trg_jurado_no_candidato | Candidato no puede ser jurado |
| ORA-20061 | trg_jurado_no_candidato | Jurado inhabilitado |
| ORA-20070 | prc_registrar_voto | Votante no puede votar |
| ORA-20075 | prc_registrar_voto | Sin biometría activa |
| ORA-20080 | fnc_peso_voto_votante | Sin configuración de peso |
| ORA-20090 | trg_proteger_votos_update | Voto inmutable — Art. 258 |
| ORA-20091 | trg_proteger_votos_delete | Voto inmutable — Art. 258 |
| ORA-20092 | trg_proteger_registro_update | Registro inmutable |
| ORA-20093 | trg_proteger_registro_delete | Registro inmutable |
| ORA-20100 | prc_inhabilitar_votante | Votante no existe |
| ORA-20101 | prc_inhabilitar_votante | Motivo obligatorio |
| ORA-20102 | prc_inhabilitar_votante | Ya inhabilitado |
| ORA-20103 | prc_inhabilitar_votante | Ya votó |
| ORA-20130 | prc_enrolar_biometria | Votante no existe |
| ORA-20131 | prc_enrolar_biometria | Votante inhabilitado |
| ORA-20132 | prc_enrolar_biometria | Hash obligatorio |
| ORA-20133 | prc_enrolar_biometria | Hash no es SHA-256 |
| ORA-20140 | prc_asignar_jurado | Mesa no existe |
| ORA-20143 | prc_asignar_jurado | Puesto incorrecto |
| ORA-20144 | prc_asignar_jurado | Ya es jurado en esa mesa |

### 6.4 Notas importantes para el desarrollador

- **El envío de correo va en la aplicación**, no en la base de datos. El voto se confirma con el COMMIT — el correo es una notificación independiente que no debe afectar la transacción electoral.
- **La selección de jurados** (quién será jurado) va en la aplicación. La base de datos valida que sea correcto, pero no decide quién.
- **La autenticación biométrica** (comparar huella contra plantilla) va en la aplicación usando la plantilla almacenada en BIOMETRIA_VOTANTES. La base de datos almacena y protege la plantilla.
- **Los huecos en secuencias** son normales en Oracle y no representan errores. No intentar resetearlas en producción.
- **Para resetear datos en desarrollo** usar `prc_reset_datos_prueba` o `prc_eliminar_votante_prueba`.

---

*Documento generado: Mayo 2026 — ABIS-UPC v1.0 — Primera entrega*
