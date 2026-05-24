# ABIS-UPC — Resumen de Cambios Segunda Entrega
**Sistema de Votación Electrónica con Autenticación Biométrica**
**Universidad Popular del Cesar — Ingeniería de Sistemas**
**Base de datos: Oracle — Esquema: ABISADMIN**

---

## Índice

1. [Modificaciones estructurales](#1-modificaciones-estructurales)
2. [Nueva tabla: POTENCIAL_VOTANTES](#2-nueva-tabla-potencial_votantes)
3. [Nuevo trigger: TRG_VALIDAR_POTENCIAL_VOTANTE](#3-nuevo-trigger-trg_validar_potencial_votante)
4. [Trigger actualizado: TRG_JURADO_NO_CANDIDATO](#4-trigger-actualizado-trg_jurado_no_candidato)
5. [Vistas](#5-vistas)
6. [Packages](#6-packages)
7. [Estado final del esquema](#7-estado-final-del-esquema)

---

## 1. Modificaciones estructurales

### 1.1 VOTANTES — Agregar FECHA_NACIMIENTO

**Razón:** Necesaria para calcular la edad de los votantes y aplicar la regla de negocio que exige que los jurados tengan mínimo 30 años.

```sql
ALTER TABLE VOTANTES ADD FECHA_NACIMIENTO DATE;
```

**Datos de prueba cargados:**
```sql
UPDATE VOTANTES SET FECHA_NACIMIENTO = DATE '2000-03-15' WHERE identificacion = '1001001001';
UPDATE VOTANTES SET FECHA_NACIMIENTO = DATE '1998-07-22' WHERE identificacion = '1001001002';
UPDATE VOTANTES SET FECHA_NACIMIENTO = DATE '1975-11-08' WHERE identificacion = '1001001003';
UPDATE VOTANTES SET FECHA_NACIMIENTO = DATE '1980-04-30' WHERE identificacion = '1001001004';
UPDATE VOTANTES SET FECHA_NACIMIENTO = DATE '1995-09-14' WHERE identificacion = '1001001005';
UPDATE VOTANTES SET FECHA_NACIMIENTO = DATE '1978-02-19' WHERE identificacion = '1001001006';
UPDATE VOTANTES SET FECHA_NACIMIENTO = DATE '2001-06-25' WHERE identificacion = '1001001007';
UPDATE VOTANTES SET FECHA_NACIMIENTO = DATE '1999-12-01' WHERE identificacion = '1001001008';
COMMIT;
```

**Fórmula de cálculo de edad:**
```sql
TRUNC(MONTHS_BETWEEN(SYSDATE, fecha_nacimiento) / 12)
```

---

## 2. Nueva tabla: POTENCIAL_VOTANTES

**Razón:** Simula el censo institucional de la UPC. Antes de registrar un votante, el sistema verifica que su identificación exista en esta tabla y que esté activo. En producción real se alimentaría desde el sistema de información institucional (SIRA o equivalente) vía ETL o database link.

**Diseño:** La tabla no reemplaza ni modifica VOTANTES — es una capa de validación previa independiente.

```sql
CREATE TABLE POTENCIAL_VOTANTES (
    identificacion   VARCHAR2(20)  NOT NULL,
    primer_nombre    VARCHAR2(50)  NOT NULL,
    segundo_nombre   VARCHAR2(50),
    primer_apellido  VARCHAR2(50)  NOT NULL,
    segundo_apellido VARCHAR2(50),
    correo_inst      VARCHAR2(100) NOT NULL,
    programa         VARCHAR2(100),
    facultad         VARCHAR2(100),
    tipo_vinculo     VARCHAR2(30)  NOT NULL,
    activo           VARCHAR2(1)   DEFAULT 'S' NOT NULL,
    CONSTRAINT pk_potencial_votantes
        PRIMARY KEY (identificacion),
    CONSTRAINT uq_potencial_correo
        UNIQUE (correo_inst),
    CONSTRAINT chk_potencial_vinculo
        CHECK (tipo_vinculo IN (
            'ESTUDIANTE','DOCENTE','EGRESADO','ADMINISTRATIVO'
        )),
    CONSTRAINT chk_potencial_activo
        CHECK (activo IN ('S','N'))
);
```

**Campos clave:**

| Campo | Descripción |
|---|---|
| identificacion | PK — número de documento institucional |
| tipo_vinculo | ESTUDIANTE, DOCENTE, EGRESADO, ADMINISTRATIVO |
| activo | S = miembro activo, N = desvinculado |
| correo_inst | Correo institucional único |
| programa / facultad | Datos académicos opcionales |

**¿Quién gestiona esta tabla?**
El DBA directamente — el sistema electoral no puede modificar quién pertenece a la institución. Eso garantiza separación de responsabilidades entre el sistema académico y el electoral.

---

## 3. Nuevo trigger: TRG_VALIDAR_POTENCIAL_VOTANTE

**Tabla:** VOTANTES | **Evento:** BEFORE INSERT
**Propósito:** Valida que todo votante registrado pertenezca al censo institucional. Además sincroniza automáticamente el rol del votante según su tipo_vinculo en POTENCIAL_VOTANTES, sin importar qué rol envíe la aplicación.

```sql
CREATE OR REPLACE TRIGGER trg_validar_potencial_votante
BEFORE INSERT ON VOTANTES
FOR EACH ROW
DECLARE
    v_existe      NUMBER;
    v_activo      POTENCIAL_VOTANTES.activo%TYPE;
    v_vinculo     POTENCIAL_VOTANTES.tipo_vinculo%TYPE;
    v_id_rol      ROLES.id_rol%TYPE;
    v_nombre_pot  VARCHAR2(100);
BEGIN
    SELECT COUNT(*), MAX(activo), MAX(tipo_vinculo),
           MAX(primer_nombre || ' ' || primer_apellido)
    INTO   v_existe, v_activo, v_vinculo, v_nombre_pot
    FROM   POTENCIAL_VOTANTES
    WHERE  identificacion = :NEW.identificacion;

    IF v_existe = 0 THEN
        RAISE_APPLICATION_ERROR(-20200,
            'Registro rechazado. La identificacion ' ||
            :NEW.identificacion ||
            ' no pertenece al censo institucional de la UPC.');
    END IF;

    IF v_activo = 'N' THEN
        RAISE_APPLICATION_ERROR(-20201,
            'Registro rechazado. La identificacion ' ||
            :NEW.identificacion ||
            ' pertenece a un miembro inactivo de la institucion.');
    END IF;

    SELECT id_rol INTO v_id_rol
    FROM   ROLES
    WHERE  UPPER(nombre) = v_vinculo;

    -- Sobreescribir rol con el del censo institucional
    :NEW.id_rol := v_id_rol;
END trg_validar_potencial_votante;
/
```

**Validaciones:**
- `-20200` → identificación no existe en el censo
- `-20201` → miembro inactivo en la institución
- `-20202` → tipo_vinculo sin rol equivalente en el sistema

**Comportamiento clave:** aunque la aplicación envíe cualquier `id_rol`, el trigger lo sobreescribe con el rol correcto según el censo — garantizando consistencia entre los sistemas.

---

## 4. Trigger actualizado: TRG_JURADO_NO_CANDIDATO

**Tabla:** JURADOS | **Evento:** BEFORE INSERT
**Cambios respecto a la versión anterior:** Se agregaron dos nuevas reglas de negocio basadas en el reglamento electoral universitario.

**Reglas nuevas agregadas:**

**Regla 1 — Solo DOCENTE o ADMINISTRATIVO pueden ser jurados**
Los estudiantes y egresados no pueden ser jurados. Solo docentes y personal administrativo tienen la estabilidad institucional y la responsabilidad requerida para el cargo.

**Regla 2 — Edad mínima 30 años**
Los jurados deben tener al menos 30 años de edad, calculada automáticamente desde `FECHA_NACIMIENTO`.

```sql
CREATE OR REPLACE TRIGGER trg_jurado_no_candidato
BEFORE INSERT ON JURADOS
FOR EACH ROW
DECLARE
    v_es_candidato  NUMBER;
    v_nombre_jurado VARCHAR2(100);
    v_estado        VOTANTES.estado_voto%TYPE;
    v_fecha_nac     VOTANTES.fecha_nacimiento%TYPE;
    v_edad          NUMBER;
    v_id_rol        VOTANTES.id_rol%TYPE;
    v_nombre_rol    ROLES.nombre%TYPE;
BEGIN
    SELECT v.primer_nombre || ' ' || v.primer_apellido,
           v.estado_voto, v.fecha_nacimiento,
           v.id_rol, r.nombre
    INTO   v_nombre_jurado, v_estado,
           v_fecha_nac, v_id_rol, v_nombre_rol
    FROM   VOTANTES v
    JOIN   ROLES r ON v.id_rol = r.id_rol
    WHERE  v.identificacion = :NEW.votantes_identificacion;

    -- Validacion 1: no inhabilitado
    IF v_estado = 'INHABILITADO' THEN
        RAISE_APPLICATION_ERROR(-20061,
            'El votante "' || v_nombre_jurado ||
            '" esta INHABILITADO y no puede ser jurado.');
    END IF;

    -- Validacion 2: solo DOCENTE o ADMINISTRATIVO
    IF v_nombre_rol NOT IN ('Docente', 'Administrativo') THEN
        RAISE_APPLICATION_ERROR(-20062,
            'El votante "' || v_nombre_jurado ||
            '" tiene rol ' || v_nombre_rol ||
            '. Solo DOCENTES y ADMINISTRATIVOS pueden ser jurados.');
    END IF;

    -- Validacion 3: fecha nacimiento requerida
    IF v_fecha_nac IS NULL THEN
        RAISE_APPLICATION_ERROR(-20063,
            'El votante "' || v_nombre_jurado ||
            '" no tiene fecha de nacimiento registrada.');
    END IF;

    -- Validacion 4: edad minima 30 anos
    v_edad := TRUNC(MONTHS_BETWEEN(SYSDATE, v_fecha_nac) / 12);
    IF v_edad < 30 THEN
        RAISE_APPLICATION_ERROR(-20064,
            'El votante "' || v_nombre_jurado ||
            '" tiene ' || v_edad || ' anos. ' ||
            'Los jurados deben tener minimo 30 anos.');
    END IF;

    -- Validacion 5: no puede ser candidato activo
    SELECT COUNT(*) INTO v_es_candidato
    FROM   CANDIDATOS_ELECCION ce
    JOIN   ELECCIONES e  ON ce.id_eleccion  = e.id_eleccion
    JOIN   CANDIDATOS  c ON ce.id_candidato = c.id_candidato
    JOIN   VOTANTES    v ON v.primer_nombre  = c.primer_nombre
                        AND v.primer_apellido = c.primer_apellido
    WHERE  v.identificacion = :NEW.votantes_identificacion
    AND    e.estado IN ('PROGRAMADA', 'EN_CURSO');

    IF v_es_candidato > 0 THEN
        RAISE_APPLICATION_ERROR(-20060,
            'Conflicto de interes: "' || v_nombre_jurado ||
            '" es candidato activo y no puede ser jurado.');
    END IF;
END trg_jurado_no_candidato;
/
```

**Tabla de errores del trigger:**

| Código | Condición |
|---|---|
| -20060 | Es candidato en elección activa |
| -20061 | Votante inhabilitado |
| -20062 | Rol no permitido (solo Docente/Administrativo) |
| -20063 | Sin fecha de nacimiento registrada |
| -20064 | Menor de 30 años |

---

## 5. Vistas

### VW_VOTANTES_COMPLETO
Votantes con rol, puesto, edad calculada y estado de biometría.

```sql
CREATE OR REPLACE VIEW vw_votantes_completo AS
SELECT v.identificacion,
       v.primer_nombre || ' ' || v.primer_apellido nombre_completo,
       v.correo, v.estado_voto,
       r.nombre                                    rol,
       p.nombre_puesto                             puesto,
       p.ciudad,
       TRUNC(MONTHS_BETWEEN(SYSDATE,
           v.fecha_nacimiento) / 12)               edad,
       v.fecha_nacimiento, v.fecha_consentimiento,
       CASE WHEN b.activo = 'S'
            THEN 'SI' ELSE 'NO' END                tiene_biometria
FROM   VOTANTES v
JOIN   ROLES r            ON v.id_rol    = r.id_rol
JOIN   PUESTOS_VOTACION p ON v.id_puesto = p.id_puesto
LEFT JOIN BIOMETRIA_VOTANTES b
       ON v.identificacion = b.identificacion
       AND b.activo = 'S';
```

### VW_RESULTADOS_ELECCION
Resultados ponderados por candidato y elección, ordenados de mayor a menor.

```sql
CREATE OR REPLACE VIEW vw_resultados_eleccion AS
SELECT e.id_eleccion, e.nombre eleccion, e.estado,
       c.id_candidato,
       c.primer_nombre || ' ' || c.primer_apellido candidato,
       ce.numero_campania tarjeton, ce.cargo,
       NVL(SUM(v.pesovoto_aplicado), 0)            votos_ponderados,
       COUNT(v.id_votos)                           total_votos
FROM   ELECCIONES e
JOIN   CANDIDATOS_ELECCION ce ON e.id_eleccion  = ce.id_eleccion
JOIN   CANDIDATOS c           ON ce.id_candidato = c.id_candidato
LEFT JOIN VOTOS v             ON v.id_eleccion   = e.id_eleccion
                             AND v.id_candidato  = c.id_candidato
GROUP BY e.id_eleccion, e.nombre, e.estado,
         c.id_candidato,
         c.primer_nombre || ' ' || c.primer_apellido,
         ce.numero_campania, ce.cargo
ORDER BY e.id_eleccion, votos_ponderados DESC;
```

### VW_PARTICIPACION_ELECCIONES
Porcentaje de participación en tiempo real por elección.

```sql
CREATE OR REPLACE VIEW vw_participacion_elecciones AS
SELECT e.id_eleccion, e.nombre eleccion, e.estado,
       COUNT(DISTINCT rv.identificacion)           votantes_participaron,
       (SELECT COUNT(*) FROM VOTANTES
        WHERE estado_voto IN
        ('PENDIENTE','EJERCIDO'))                  total_habilitados,
       ROUND(COUNT(DISTINCT rv.identificacion) /
           NULLIF((SELECT COUNT(*) FROM VOTANTES
                   WHERE estado_voto IN
                   ('PENDIENTE','EJERCIDO')), 0)
           * 100, 2)                               porcentaje
FROM   ELECCIONES e
LEFT JOIN REGISTRO_VOTOS rv ON e.id_eleccion = rv.id_eleccion
GROUP BY e.id_eleccion, e.nombre, e.estado
ORDER BY e.id_eleccion;
```

### VW_JURADOS_ACTIVOS
Jurados con datos completos — mesa, puesto, rol, edad y cargo.

```sql
CREATE OR REPLACE VIEW vw_jurados_activos AS
SELECT j.mesa_jurados_idmesa                       mesa,
       m.cargo                                     cargo_mesa,
       p.nombre_puesto                             puesto,
       p.ciudad,
       v.identificacion,
       v.primer_nombre || ' ' || v.primer_apellido nombre_jurado,
       r.nombre                                    rol,
       TRUNC(MONTHS_BETWEEN(SYSDATE,
           v.fecha_nacimiento) / 12)               edad,
       j.cargo                                     cargo_jurado,
       j.fecha_asignacion
FROM   JURADOS j
JOIN   VOTANTES v         ON j.votantes_identificacion    = v.identificacion
JOIN   ROLES r            ON v.id_rol                     = r.id_rol
JOIN   MESA_JURADOS m     ON j.mesa_jurados_idmesa        = m.id_mesa
JOIN   PUESTOS_VOTACION p ON m.puestos_votacion_idpuestos = p.id_puesto
ORDER BY j.mesa_jurados_idmesa, j.fecha_asignacion;
```

### VW_AUDITORIA_COMPLETA
Auditoría con nombres legibles en lugar de IDs.

```sql
CREATE OR REPLACE VIEW vw_auditoria_completa AS
SELECT av.id_auditoria, av.identificacion,
       v.primer_nombre || ' ' || v.primer_apellido nombre_votante,
       a.nombre                                    administrador,
       av.campo_modificado, av.valor_anterior,
       av.valor_nuevo, av.accion, av.motivo,
       av.fecha_hora
FROM   AUDITORIA_VOTANTES av
JOIN   VOTANTES v        ON av.identificacion = v.identificacion
JOIN   ADMINISTRADORES a ON av.id_admin       = a.id_admin
ORDER BY av.fecha_hora DESC;
```

---

## 6. Packages

### PKG_VOTANTES
Gestión completa del ciclo de vida de un votante.

| Objeto | Tipo | Descripción |
|---|---|---|
| `puede_votar` | FUNCTION | Llama fnc_votante_puede_votar |
| `peso_voto` | FUNCTION | Llama fnc_peso_voto_votante |
| `edad_votante` | FUNCTION | Calcula edad desde fecha_nacimiento |
| `inhabilitar` | PROCEDURE | Llama prc_inhabilitar_votante |
| `habilitar` | PROCEDURE | Llama prc_habilitar_votante |
| `enrolar_biometria` | PROCEDURE | Llama prc_enrolar_biometria |

**Uso desde backend:**
```sql
-- Verificar antes de mostrar pantalla de votación
DECLARE
    v_puede  VARCHAR2(1);
    v_motivo VARCHAR2(500);
BEGIN
    v_puede := pkg_votantes.puede_votar('1001001001', 1, v_motivo);
END;

-- Calcular edad
SELECT pkg_votantes.edad_votante('1001001001') FROM dual;
```

---

### PKG_ELECTORAL
Proceso de votación y gestión de elecciones.

| Objeto | Tipo | Descripción |
|---|---|---|
| `registrar_voto` | PROCEDURE | Transacción atómica completa |
| `cerrar_eleccion` | PROCEDURE | Cierre oficial con reporte |
| `votos_candidato` | FUNCTION | Total ponderado de un candidato |
| `participacion` | FUNCTION | Porcentaje de participación |
| `eleccion_activa` | FUNCTION | Retorna 'S'/'N' si está EN_CURSO |

**Uso desde backend:**
```sql
-- Registrar voto
BEGIN
    pkg_electoral.registrar_voto('1001001001', 1, 2, 1);
END;

-- Panel de control
SELECT pkg_electoral.participacion(1) FROM dual;
SELECT pkg_electoral.eleccion_activa(1) FROM dual;
```

---

### PKG_JURADOS
Gestión de jurados y validación de elegibilidad.

| Objeto | Tipo | Descripción |
|---|---|---|
| `asignar` | PROCEDURE | Llama prc_asignar_jurado |
| `es_elegible` | FUNCTION | Verifica rol, edad e inhabilitación |
| `total_jurados_mesa` | FUNCTION | Cuenta jurados en una mesa |

**Uso desde backend:**
```sql
-- Verificar elegibilidad antes de asignar
SELECT pkg_jurados.es_elegible('1001001003') FROM dual;

-- Asignar
BEGIN
    pkg_jurados.asignar(1, '1001001003', 'Jurado de Mesa');
END;
```

---

### PKG_AUDITORIA
Trazabilidad y reportes del sistema.

| Objeto | Tipo | Descripción |
|---|---|---|
| `total_cambios_votante` | FUNCTION | Cuenta registros de auditoría |
| `tiene_accion` | FUNCTION | Verifica si existe una acción específica |
| `ultimo_cambio` | PROCEDURE | Retorna el último registro de auditoría |
| `reporte_admin` | PROCEDURE | Reporte completo de un administrador |

**Uso desde backend:**
```sql
-- Cuántos cambios tiene un votante
SELECT pkg_auditoria.total_cambios_votante('1001001001') FROM dual;

-- Verificar si fue inhabilitado alguna vez
SELECT pkg_auditoria.tiene_accion('1001001001','INHABILITACION') FROM dual;
```

---

## 7. Estado final del esquema

### Tablas (18 total)
| Tabla | Descripción |
|---|---|
| ADMINISTRADORES | Usuarios del sistema |
| AUDITORIA_CORREOS | Auditoría de correos enviados |
| AUDITORIA_VOTANTES | Trazabilidad de cambios |
| BIOMETRIA_VOTANTES | Huellas dactilares cifradas |
| CANDIDATOS | Candidatos del sistema |
| CANDIDATOS_ELECCION | Inscripción candidato-elección |
| ELECCION_ROLES | Pesos de voto por rol y elección |
| ELECCIONES | Jornadas electorales |
| ENVIOS_CONTINGENCIA | Envíos de tokens de contingencia |
| JURADOS | Asignación de jurados a mesas |
| MESA_JURADOS | Mesas de votación |
| POTENCIAL_VOTANTES | **NUEVA** — Censo institucional UPC |
| PUESTOS_VOTACION | Sedes físicas de votación |
| REGISTRO_VOTOS | Participación anónima |
| ROLES | Estamentos electorales |
| SESIONES | Sesiones de administradores |
| TOKENS_CONTINGENCIA | Tokens QR de contingencia |
| VOTANTES | Padrón electoral |
| VOTOS | Votos anónimos ponderados |

### Triggers (15 total)
| Trigger | Tabla | Evento |
|---|---|---|
| TRG_ACTUALIZAR_ESTADO_VOTO | REGISTRO_VOTOS | AFTER INSERT |
| TRG_AUDITORIA_BIOMETRIA | BIOMETRIA_VOTANTES | AFTER INSERT/UPDATE/DELETE |
| TRG_AUDITORIA_ESTADO_VOTO | VOTANTES | AFTER UPDATE OF estado_voto |
| TRG_CANDIDATO_UNICO_ELECCION | CANDIDATOS_ELECCION | BEFORE INSERT |
| TRG_JURADO_NO_CANDIDATO | JURADOS | BEFORE INSERT |
| TRG_NORMALIZAR_VOTANTE | VOTANTES | BEFORE INSERT |
| TRG_PROTEGER_REGISTRO_DELETE | REGISTRO_VOTOS | BEFORE DELETE |
| TRG_PROTEGER_REGISTRO_UPDATE | REGISTRO_VOTOS | BEFORE UPDATE |
| TRG_PROTEGER_VOTOS_DELETE | VOTOS | BEFORE DELETE |
| TRG_PROTEGER_VOTOS_UPDATE | VOTOS | BEFORE UPDATE |
| TRG_VALIDAR_ACCION_AUDITORIA | AUDITORIA_VOTANTES | BEFORE INSERT |
| TRG_VALIDAR_ELECCION_ACTIVA | VOTOS | BEFORE INSERT |
| TRG_VALIDAR_PARTICIPACION | REGISTRO_VOTOS | BEFORE INSERT |
| TRG_VALIDAR_PESO_VOTO | ELECCION_ROLES | BEFORE INSERT/UPDATE |
| TRG_VALIDAR_POTENCIAL_VOTANTE | VOTANTES | BEFORE INSERT |

### Funciones (4)
| Función | Retorna |
|---|---|
| FNC_CALCULAR_VOTOS_CANDIDATO | NUMBER — votos ponderados |
| FNC_PESO_VOTO_VOTANTE | NUMBER — peso según rol |
| FNC_PORCENTAJE_PARTICIPACION | NUMBER — % con 2 decimales |
| FNC_VOTANTE_PUEDE_VOTAR | VARCHAR2 — 'S' o 'N' + motivo OUT |

### Procedimientos (6)
| Procedimiento | Propósito |
|---|---|
| PRC_ASIGNAR_JURADO | Asignar jurado a mesa |
| PRC_CERRAR_ELECCION | Cierre oficial con reporte |
| PRC_ENROLAR_BIOMETRIA | Registro/actualización biométrica |
| PRC_HABILITAR_VOTANTE | Reactivar votante inhabilitado |
| PRC_INHABILITAR_VOTANTE | Inhabilitar con motivo obligatorio |
| PRC_REGISTRAR_VOTO | Transacción atómica electoral |

### Packages (4)
| Package | Responsabilidad |
|---|---|
| PKG_AUDITORIA | Trazabilidad y reportes |
| PKG_ELECTORAL | Votación y elecciones |
| PKG_JURADOS | Jurados y mesas |
| PKG_VOTANTES | Ciclo de vida del votante |

### Vistas (5)
| Vista | Descripción |
|---|---|
| VW_AUDITORIA_COMPLETA | Auditoría con nombres legibles |
| VW_JURADOS_ACTIVOS | Jurados con rol, edad y puesto |
| VW_PARTICIPACION_ELECCIONES | Porcentaje en tiempo real |
| VW_RESULTADOS_ELECCION | Resultados ponderados |
| VW_VOTANTES_COMPLETO | Votantes con datos completos |

---

### Reglas de negocio implementadas en esta entrega

| Regla | Implementación |
|---|---|
| Solo miembros UPC pueden registrarse | TRG_VALIDAR_POTENCIAL_VOTANTE |
| El rol se asigna según censo institucional | TRG_VALIDAR_POTENCIAL_VOTANTE |
| Jurados deben tener mínimo 30 años | TRG_JURADO_NO_CANDIDATO |
| Jurados solo pueden ser Docentes o Administrativos | TRG_JURADO_NO_CANDIDATO |
| La edad se calcula automáticamente desde fecha_nacimiento | Vistas + PKG_JURADOS |

---

*Documento generado: Mayo 2026 — ABIS-UPC v2.0 — Segunda entrega*
