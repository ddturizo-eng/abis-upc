# AGENTS.md — ABIS-UPC

> Contexto completo del sistema para agentes de IA (Cursor, Windsurf, Copilot, Claude Code).
> Leer este archivo antes de tocar cualquier código del proyecto.

---

## 1. Qué es este sistema

**ABIS-UPC** (Automated Biometric Identification System) es una plataforma de votación electrónica biométrica para la Universidad Popular del Cesar. Permite registrar votantes mediante OCR de cédulas colombianas y huella dactilar, y gestionar jornadas electorales con anonimato garantizado del sufragio.

- **Materia:** Programación de Computadores III (SS462) — Ing. Esp. Alfredo Bautista
- **Universidad:** Universidad Popular del Cesar
- **Repositorio:** https://github.com/ddturizo-eng/abis-upc.git
- **Año:** 2026

---

## 2. Equipo y responsabilidades Git

| Integrante | Rol | Ramas |
|---|---|---|
| Daniel Turizo | Tech Lead + Full Stack | `develop`, `feature/biometric`, `feature/services`, `feature/frontend`, `feature/integration` |
| Daniel Flórez | Backend Security | `feature/security` |
| Mateo Calderón | Backend DAO | `feature/dao-oracle` |
| Jorge Herrera | Backend DAO | `feature/dao-oracle` |

---

## 3. Arquitectura general

El sistema es una arquitectura distribuida de **tres capas de servicios** que se comunican por HTTP/REST:

```
[Frontend HTML/JS/CSS]
        │
        ▼
[Backend Java — Javalin :7000]   ←── AuthMiddleware (token en SESIONES)
        │
        ├──► [FastAPI Biométrico :8001]  ←── [NativeService C# :8765]
        │         └── Oracle XE :1521
        │
        └──► [FastAPI OCR :8002]
                  └── PaddleOCR / Tesseract
```

### Servicios y puertos

| Servicio | Puerto | Tecnología | Descripción |
|---|---|---|---|
| abis-backend | 7000 | Java + Javalin | Backend principal, lógica de negocio, REST API, sirve el frontend estático |
| abis-biometric | 8001 | Python + FastAPI | Enrolamiento y verificación de huella dactilar |
| abis-ocr | 8002 | Python + FastAPI | OCR de documentos de identidad colombianos |
| abis-native | 8765 | C# + .NET 4.8 | Interfaz WebSocket con el lector DigitalPersona / AS608 |
| Oracle XE | 1521 | Oracle Database XE | Base de datos principal |

### Estructura de directorios

```
abis-upc/
├── abis-backend/           # Java (Javalin) — Puerto 7000
│   └── src/main/
│       ├── java/com/abisupc/
│       │   ├── config/         # AppConfig — pool HikariCP
│       │   ├── controller/     # Controladores REST
│       │   ├── dto/            # DTOs de transferencia
│       │   ├── integration/    # BiometricClient HTTP
│       │   ├── model/          # Entidades del dominio
│       │   ├── repository/     # Acceso a Oracle (JDBC)
│       │   ├── security/       # AuthMiddleware
│       │   ├── service/        # Lógica de negocio
│       │   └── server/         # AppServer — punto de entrada
│       └── resources/          # Frontend estático (HTML/JS/CSS)
│           ├── pages/
│           │   ├── auth/login.html
│           │   ├── biometric/enroll.html
│           │   ├── biometric/verify.html
│           │   └── registro/   # Flujo de 4 pasos
│           ├── js/
│           │   ├── api.js          # Cliente HTTP del frontend
│           │   ├── router.js       # Navegación SPA
│           │   └── votante-session.js
│           └── styles/base.css
├── abis-biometric/         # Python FastAPI — Puerto 8001
│   └── app/
│       ├── main.py
│       ├── db/database.py      # Pool de conexión Oracle
│       ├── routers/            # enroll.py, verify.py, vote.py
│       └── services/native_client.py
├── abis-ocr/               # Python FastAPI — Puerto 8002
│   └── ocr/                # Motores, parsers, clasificación
├── abis-native/            # C# .NET — Puerto 8765 WebSocket
├── abis-database/          # Scripts SQL Oracle
│   ├── ddl/01_create_tables.sql
│   ├── ddl/02_foreign_keys.sql
│   └── dml/datos_maestros.sql
└── docs/                   # Documentación del proyecto
```

---

## 4. Base de datos Oracle XE

### Esquema: todas las tablas

#### ROLES
Tipos de votante. Define el tipo de votante para la universidad.

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_ROL | NUMBER | PK | Identificador único |
| NOMBRE | VARCHAR2 | — | ESTUDIANTE / DOCENTE / EGRESADO / ADMINISTRATIVO |

Secuencia: `seq_roles`

---

#### PUESTOS_VOTACION
Ubicaciones físicas donde se instalan los kioscos.

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_PUESTOS | NUMBER | PK | Identificador único |
| CIUDAD | VARCHAR2 | — | Ciudad del puesto |
| SEDE | VARCHAR2 | — | Sede universitaria (SEDE CENTRAL, SEDE RIO, etc.) |
| NOMBRE_PUESTO | VARCHAR2 | — | Nombre descriptivo del puesto |
| HORA_INICIO | TIMESTAMP | NOT NULL | Hora de inicio de la jornada |
| HORA_SALIDA | TIMESTAMP | NOT NULL | Hora de cierre de la jornada |
| CHECK | — | — | HORA_SALIDA > HORA_INICIO |

Secuencia: `seq_puestos_votacion`

---

#### VOTANTES
Censo electoral completo. Tabla central del sistema.

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| IDENTIFICACION | VARCHAR2(15) | PK | Número de cédula / TI / documento |
| CORREO | VARCHAR2 | — | Correo electrónico |
| PRIMER_NOMBRE | VARCHAR2 | — | Primer nombre |
| SEGUNDO_NOMBRE | VARCHAR2 | — | Segundo nombre (opcional) |
| PRIMER_APELLIDO | VARCHAR2 | — | Primer apellido |
| SEGUNDO_APELLIDO | VARCHAR2 | — | Segundo apellido |
| ESTADO_VOTO | VARCHAR2 | CHECK | `PENDIENTE` / `EJERCIDO` / `INHABILITADO` |
| FOTO_URL | VARCHAR2 | — | Ruta de la foto capturada en enrolamiento |
| FECHA_CONSENTIMIENTO | DATE | — | Fecha de aceptación de uso biométrico (Ley 1581) |
| QR_CEDULA | VARCHAR2(500) | nullable | String raw del PDF417 de la cédula (segunda llave de acceso) |
| ID_ROL | NUMBER | FK → ROLES | Rol del votante |
| ID_PUESTOS | NUMBER | FK → PUESTOS_VOTACION | Puesto asignado |

> **Sin secuencia** — PK es la cédula real del votante.
> **Nota:** Los datos biométricos se almacenan en BIOMETRIA_VOTANTES (separación por Ley 1581)

---

#### MESA_JURADOS
Mesas operativas dentro de un puesto durante la jornada.

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_MESA | NUMBER | PK | Identificador único |
| HORA_INGRESO | DATE | — | Inicio del turno |
| HORA_SALIDA | DATE | — | Fin del turno |
| CARGO | VARCHAR2 | — | Cargo de la mesa (Presidente, Vocal, etc.) |
| ID_PUESTOS | NUMBER | FK → PUESTOS_VOTACION | Puesto al que pertenece |

Secuencia: `seq_mesa_jurados`

---

#### JURADOS
Asignación de un votante como jurado en una mesa específica.

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_MESA | NUMBER | PK compuesta + FK → MESA_JURADOS | Mesa asignada |
| IDENTIFICACION | VARCHAR2 | PK compuesta + FK → VOTANTES | Votante que actúa como jurado |
| FECHA_ASIGNACION | DATE | — | Cuándo fue asignado |
| CARGO | VARCHAR2 | — | Cargo dentro de la mesa |

> **Sin secuencia** — PK compuesta (ID_MESA + IDENTIFICACION).
> **No implementa Repository\<T\>** por la PK compuesta.

---

#### ELECCIONES
Cada proceso electoral abierto en la universidad.

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_ELECCION | NUMBER | PK | Identificador único |
| NOMBRE | VARCHAR2 | — | Nombre descriptivo de la elección |
| FECHA_HORA_INICIO | TIMESTAMP | — | Fecha y hora de apertura |
| FECHA_HORA_FIN | TIMESTAMP | — | Fecha y hora de cierre |
| ESTADO | VARCHAR2 | CHECK | `PROGRAMADA` / `EN_CURSO` / `CERRADA` |

Secuencia: `seq_elecciones`
> Solo puede haber una elección con estado `EN_CURSO` simultáneamente.

---

#### CANDIDATOS
Postulados (personas) que pueden competir en elecciones. Un candidato puede postularse en múltiples elecciones con diferentes cargos.

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_CANDIDATO | NUMBER | PK | Identificador único |
| PRIMER_NOMBRE | VARCHAR2 | — | Primer nombre |
| SEGUNDO_NOMBRE | VARCHAR2 | — | Segundo nombre |
| PRIMER_APELLIDO | VARCHAR2 | — | Primer apellido |
| SEGUNDO_APELLIDO | VARCHAR2 | — | Segundo apellido |

Secuencia: `seq_candidatos`

---

#### CANDIDATOS_ELECCION
Relación entre candidatos y elecciones. Define el número de tarjetón y cargo para cada postulación.

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_CANDIDATO | NUMBER | PK compuesta + FK → CANDIDATOS | Candidato postulado |
| ID_ELECCION | NUMBER | PK compuesta + FK → ELECCIONES | Elección en la que compite |
| NUMERO_CAMPANIA | NUMBER(4) | NOT NULL | Número visible en el tarjetón |
| CARGO | VARCHAR2(100) | NOT NULL | Cargo al que aspira (Rector, Personero, etc.) |
| UNIQUE | — | — | (ID_ELECCION, NUMERO_CAMPANIA) |

---

#### ELECCION_ROLES
Configuración del peso de voto por elección y por rol. Permite que cada elección tenga ponderaciones diferentes.

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_ELECCION | NUMBER | PK compuesta + FK → ELECCIONES | Elección configurada |
| ID_ROL | NUMBER | PK compuesta + FK → ROLES | Rol al que aplica el peso |
| PESO_VOTO | NUMBER(5,2) | NOT NULL, CHECK > 0 | Factor de ponderación del voto |
| FECHA_CONFIGURACION | DATE | DEFAULT SYSDATE | Cuándo se configuró |

---

#### BIOMETRIA_VOTANTES
Almacenamiento de plantillas biométricas. Tabla separada de VOTANTES por requerimientos de Ley 1581 (separación de datos sensibles).

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_BIOMETRIA | NUMBER | PK | Identificador único |
| IDENTIFICACION | VARCHAR2(20) | FK → VOTANTES, UNIQUE | Votante enrolado |
| PLANTILLA_BIOMETRICA | BLOB | NOT NULL | Template de huella cifrado AES-256 |
| HASHINTEGRIDADBIOMETRICA | VARCHAR2(256) | NOT NULL | SHA-256 del template para verificar integridad |
| FECHA_ENROLAMIENTO | DATE | DEFAULT SYSDATE | Cuándo se realizó el enrolamiento |
| ACTIVO | VARCHAR2(1) | DEFAULT 'S', CHECK IN ('S','N') | Si el enrolamiento está activo |

Secuencia: `seq_biometria_votantes`
> Relación 1:1 con VOTANTES garantizada por UNIQUE(IDENTIFICACION)

---

#### VOTOS
Registro anónimo de cada voto emitido. **Nunca contiene IDENTIFICACION del votante.**

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_VOTOS | NUMBER | PK | Identificador único |
| FECHA_HORA | DATE | — | Momento exacto del voto |
| PESO_VOTO_APLICADO | NUMBER(5,2) | NOT NULL, CHECK > 0 | Peso del rol aplicado al momento de votar |
| ID_ROL | NUMBER | FK → ROLES | Rol del votante (para resultados ponderados) |
| ID_ELECCION | NUMBER | FK → ELECCIONES | Elección correspondiente |
| ID_CANDIDATO | NUMBER | FK → CANDIDATOS_ELECCION | Candidato seleccionado |

Secuencia: `seq_votos`
> La FK compuesta hacia CANDIDATOS_ELECCION impide ID_CANDIDATO NULL. El voto en blanco se maneja a nivel de lógica de negocio.

---

#### REGISTRO_VOTOS
Registro de auditoría que sí tiene identidad. **Nunca contiene ID_CANDIDATO.**

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_REGISTRO | NUMBER | PK | Identificador único |
| FECHA_HORA | DATE | — | Momento del registro |
| IDENTIFICACION | VARCHAR2 | FK → VOTANTES | Quién votó |
| ID_PUESTOS | NUMBER | FK → PUESTOS_VOTACION | Dónde votó |
| ID_ELECCION | NUMBER | FK → ELECCIONES | En qué elección |
| UNIQUE | — | — | (IDENTIFICACION, ID_ELECCION) — evita doble voto |

Secuencia: `seq_registro_votos`
> Esta tabla se usa para verificar si un votante ya ejerció su voto antes de mostrarle el tarjetón.

---

#### ADMINISTRADORES
Usuarios con acceso al panel de gestión.

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_ADMIN | NUMBER | PK | Identificador único |
| USUARIO | VARCHAR2 | UNIQUE | Nombre de usuario para login |
| PASSWORD_HASH | VARCHAR2 | — | SHA-256 de la contraseña. Nunca texto plano |
| NOMBRE | VARCHAR2 | — | Nombre completo |
| CORREO | VARCHAR2 | — | Correo electrónico |

Secuencia: `seq_administradores`

---

#### SESIONES
Tokens activos de sesión de administradores.

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_SESION | NUMBER | PK | Identificador único |
| TOKEN | VARCHAR2 | UNIQUE | UUID de sesión |
| FECHA_INICIO | DATE | — | Cuándo inició la sesión |
| FECHA_FIN | DATE | — | Cuándo cerró (NULL si está activa) |
| ID_ADMIN | NUMBER | FK → ADMINISTRADORES | A quién pertenece |

Secuencia: `seq_sesiones`

---

#### AUDITORIA_VOTANTES
Log de cada modificación realizada sobre un votante por un administrador.

| Campo | Tipo | Constraint | Descripción |
|---|---|---|---|
| ID_AUDITORIA | NUMBER | PK | Identificador único |
| IDENTIFICACION | VARCHAR2(15) | FK → VOTANTES | Votante modificado |
| ID_ADMIN | NUMBER | FK → ADMINISTRADORES | Admin que realizó la acción |
| CAMPO_MODIFICADO | VARCHAR2(50) | NOT NULL | Qué campo fue alterado |
| VALOR_ANTERIOR | VARCHAR2(500) | nullable | Valor antes del cambio |
| VALOR_NUEVO | VARCHAR2(500) | nullable | Valor después del cambio |
| MOTIVO | VARCHAR2(255) | nullable | Justificación del cambio |
| ACCION | VARCHAR2(30) | CHECK | Tipo de acción (ver valores) |
| FECHA_HORA | DATE | DEFAULT SYSDATE | Cuándo ocurrió |

Secuencia: `seq_auditoria_votantes`

Valores permitidos para ACCION:
- `EDICION_DATOS` — corrección de datos personales
- `CAMBIO_ROL` — cambio de tipo de votante
- `CAMBIO_PUESTO` — traslado a otro puesto de votación
- `INHABILITACION` — bloqueo del votante
- `HABILITACION` — desbloqueo del votante
- `RE_ENROLAMIENTO` — nuevo enrolamiento biométrico (caso pérdida de dedo)
- `ANONIMIZACION` — borrado irreversible de plantilla biométrica post-elección

---

### Relaciones entre tablas

```
ROLES ──────────────────────┬──► VOTANTES (1:N via ID_ROL)
                              └─► ELECCION_ROLES (1:N)

PUESTOS_VOTACION ───────────┬──► VOTANTES (1:N)
                              ├─► MESA_JURADOS (1:N)
                              └─► REGISTRO_VOTOS (1:N)

VOTANTES ────────────────────┬──► JURADOS (1:N)
                              ├─► REGISTRO_VOTOS (1:N)
                              ├─► AUDITORIA_VOTANTES (1:N)
                              └─► BIOMETRIA_VOTANTES (1:1)

MESA_JURADOS ────────────────► JURADOS (1:N)

ELECCIONES ──────────────────┬─► CANDIDATOS_ELECCION (1:N)
                              ├─► VOTOS (1:N)
                              ├─► REGISTRO_VOTOS (1:N)
                              └─► ELECCION_ROLES (1:N)

CANDIDATOS ──────────────────► CANDIDATOS_ELECCION (1:N)

CANDIDATOS_ELECCION ─────────► VOTOS (1:N via FK compuesta)

ADMINISTRADORES ─────────────┬──► SESIONES (1:N)
                              └─► AUDITORIA_VOTANTES (1:N)
```

### Secuencias existentes

| Secuencia | Tabla | Campo |
|---|---|---|
| seq_roles | ROLES | ID_ROL |
| seq_puestos_votacion | PUESTOS_VOTACION | ID_PUESTOS |
| seq_mesa_jurados | MESA_JURADOS | ID_MESA |
| seq_elecciones | ELECCIONES | ID_ELECCION |
| seq_candidatos | CANDIDATOS | ID_CANDIDATO |
| seq_votos | VOTOS | ID_VOTOS |
| seq_registro_votos | REGISTRO_VOTOS | ID_REGISTRO |
| seq_administradores | ADMINISTRADORES | ID_ADMIN |
| seq_sesiones | SESIONES | ID_SESION |
| seq_auditoria_votantes | AUDITORIA_VOTANTES | ID_AUDITORIA |
| seq_biometria_votantes | BIOMETRIA_VOTANTES | ID_BIOMETRIA |

> VOTANTES y JURADOS no tienen secuencia: PK natural (cédula) y PK compuesta respectivamente.

---

## 5. Principio de anonimato del sufragio

**Es la decisión de diseño más importante del sistema.**

- `VOTOS` tiene `ID_CANDIDATO` pero **no tiene `IDENTIFICACION`**
- `REGISTRO_VOTOS` tiene `IDENTIFICACION` pero **no tiene `ID_CANDIDATO`**
- Es imposible cruzar quién votó con por quién votó, incluso con acceso directo a la BD
- El único punto de escritura a ambas tablas es el stored procedure `sp_registrar_voto`
- Este SP ejecuta ambos INSERTs en una sola transacción ACID con COMMIT al final
- Si falla cualquier INSERT, hace ROLLBACK completo — nunca queda un registro huérfano

> **Nota sobre voto en blanco:** El voto en blanco no se representa con ID_CANDIDATO = NULL porque la FK compuesta hacia CANDIDATOS_ELECCION lo impide. Se maneja a nivel de lógica de negocio: existe un candidato especial 'VOTO EN BLANCO' por cargo dentro de cada elección, o se registra directamente en VOTOS con un ID_CANDIDATO reservado. Decisión a definir antes de implementar el tarjetón.

---

## 6. Flujos principales del sistema

### Flujo de registro de votante (pre-jornada)
```
Paso 1: Escaneo de documento
  → OCR del documento (cámara o archivo)
  → FastAPI OCR :8002 extrae: nombres, apellidos, identificacion, sexo, fecha_nac
  → Operador corrige datos si OCR falló
  → Se guarda QR_CEDULA (string PDF417 raw del escáner 2D)
  → INSERT en VOTANTES con ESTADO_VOTO = 'pendiente'

Paso 2: Biometría
  → Captura foto del rostro → POST /api/votantes/foto → /assets/fotos/
  → Captura 4 muestras de huella → NativeService :8765 → FastAPI Bio :8001
  → Genera plantilla, cifra AES-256, calcula SHA-256
  → UPDATE VOTANTES: PLANTILLA_BIOMETRICA, HASHINTEGRIDADBIOMETRICA, FOTO_URL, FECHA_CONSENTIMIENTO

Paso 3: Revisión
  → El operador confirma todos los datos antes de finalizar

Paso 4: Completado
  → Registro confirmado en Oracle
```

### Flujo de votación (día de elecciones)
```
1. Identificación del votante:
   Opción A: Huella dactilar → POST /api/verify → FastAPI Bio :8001
   Opción B (fallback): Escáner 2D lee PDF417 → busca por QR_CEDULA o IDENTIFICACION

2. Confirmación de identidad:
   → Sistema muestra foto, nombre, cédula, estado del votante
   → Operador/jurado confirma visualmente que es la persona correcta

3. Validación de voto previo:
   → SELECT en REGISTRO_VOTOS WHERE IDENTIFICACION = ? AND ID_ELECCION = ?
   → Si existe → "Voto ya ejercido", bloquear acceso al tarjetón

4. Tarjetón de votación:
   → Muestra candidatos agrupados por cargo de la elección EN_CURSO
   → Votante selecciona uno por cargo o "Voto en blanco"
   → Sin datos de identidad visibles en pantalla

5. Confirmación irreversible:
   → Modal con resumen de selecciones
   → El votante confirma explícitamente

6. Registro:
   → Ejecuta sp_registrar_voto (transacción ACID)
   → INSERT en VOTOS (sin identidad)
   → INSERT en REGISTRO_VOTOS (sin candidato)
   → UPDATE VOTANTES SET ESTADO_VOTO = 'ejercido'
```

### Flujo de autenticación de administrador
```
POST /api/auth/login
  → Verifica usuario + SHA-256(password) contra ADMINISTRADORES
  → Crea registro en SESIONES con TOKEN = UUID
  → Retorna token al cliente

Cada petición protegida:
  → AuthMiddleware extrae Bearer token del header
  → SELECT en SESIONES WHERE TOKEN = ? AND FECHA_FIN IS NULL
  → Si no existe o expiró → 401

POST /api/auth/logout
  → UPDATE SESIONES SET FECHA_FIN = SYSDATE WHERE TOKEN = ?
```

---

## 7. API — endpoints implementados

### Backend Java :7000

| Método | Endpoint | Descripción | Auth |
|---|---|---|---|
| GET | `/api/health` | Estado de todos los microservicios | No |
| GET | `/api/puestos` | Listar puestos de votación | No |
| POST | `/api/registro/preregistro` | Pre-registro de votante con datos OCR | No |
| POST | `/api/votantes/foto` | Subir foto del rostro del votante | No |
| POST | `/api/enroll` | Enrolamiento biométrico (delega a :8001) | No |
| POST | `/api/verify` | Verificación biométrica (delega a :8001) | No |
| POST | `/api/document/scan` | OCR de documento (delega a :8002) | No |
| POST | `/api/auth/login` | Login de administrador | No |
| POST | `/api/auth/logout` | Logout de administrador | Sí |

### FastAPI Biométrico :8001

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/health` | Health check |
| POST | `/enroll/` | Enrolamiento con DigitalPersona |
| POST | `/verify/` | Verificación de huella |
| POST | `/ocr/scan` | Escaneo de documento |

---

## 8. Convenciones de código

### Java (abis-backend)
- Nomenclatura: `camelCase` para métodos y variables, `PascalCase` para clases
- Comentarios: Javadoc en todas las clases públicas
- Cada Controller solo maneja HTTP — nunca lógica de negocio
- Cada Repository solo accede a su tabla — nunca a otras tablas directamente
- Toda conexión a Oracle va por `AppConfig.getConnection()` — nunca conexión directa
- Los INSERT usan `seq_<tabla>.NEXTVAL` explícitamente en el SQL

```java
// Patrón de INSERT con secuencia
String sql = "INSERT INTO Votantes (identificacion, ...) VALUES (?, ...)";
// PK natural — no usa secuencia

String sql = "INSERT INTO Elecciones (id_eleccion, nombre, ...) VALUES (seq_elecciones.NEXTVAL, ?, ...)";
```

### Python (abis-biometric, abis-ocr)
- Estilo: PEP8
- Type hints en todas las funciones
- Cada router en su propio archivo dentro de `routers/`

### SQL (Oracle XE)
- Nombres de tablas: `PascalCase` (Votantes, MesaJurados)
- Nombres de campos: `UPPER_SNAKE_CASE` (ID_ROL, PRIMER_NOMBRE)
- Nomenclatura de constraints:
  - PK: `pk_<campo>_<tabla>`
  - FK: `fk_<campo>_<tabla_origen>_<tabla_destino>`
  - CHECK: `chk_<campo>_<tabla>`
- Nomenclatura de secuencias: `seq_<nombre_tabla_lower>`

---

## 9. Variables de entorno (.env)

```env
# Oracle
DB_URL=jdbc:oracle:thin:@localhost:1521:XE
DB_USER=system
DB_PASSWORD=<password>

# Servicios
BIOMETRIC_SERVICE_URL=http://localhost:8001
OCR_SERVICE_URL=http://localhost:8002
NATIVE_SERVICE_URL=ws://localhost:8765

# Seguridad
KEYSTORE_PATH=./abis-upc.jks
KEYSTORE_PASSWORD=<password>
AES_KEY=<256-bit-key>
```

---

## 10. Seguridad — reglas críticas

1. **Plantillas biométricas**: siempre cifradas AES-256-CBC antes de INSERT. Nunca se almacenan en texto plano.
2. **Contraseñas de admin**: siempre SHA-256. Nunca texto plano en BD ni en logs.
3. **Secreto del sufragio**: `sp_registrar_voto` es el único punto de escritura a VOTOS y REGISTRO_VOTOS. Nunca escribir a estas tablas directamente desde el código de aplicación.
4. **Tokens de sesión**: UUID v4. Se invalidan en SESIONES al hacer logout (FECHA_FIN = SYSDATE).
5. **Datos OCR**: sanitizar antes de cualquier INSERT para prevenir SQL injection.
6. **AUDITORIA_VOTANTES**: todo cambio a datos de un votante hecho por un admin debe insertar un registro en esta tabla en la misma transacción.

---

## 11. Estado actual de implementación

### Completado ✓
- Pre-registro con OCR (Tesseract / PaddleOCR)
- Captura de foto del rostro via webcam
- Enrolamiento biométrico de huella (4 muestras, plantilla, cifrado)
- Flujo UX de 4 pasos (registro completo)
- Integración completa de los 4 servicios
- Autenticación de administrador con tokens en SESIONES
- Endpoints base del backend Java

### Pendiente — por orden de prioridad

**Bloque 0 — Infraestructura**
- [ ] Navbar/sidebar de navegación funcional entre módulos
- [x] Campo QR_CEDULA en VOTANTES
- [ ] Stored procedure `sp_registrar_voto`

**Bloque 1 — Elecciones y candidatos**
- [x] CRUD completo de elecciones (crear, editar, iniciar, cerrar, eliminar)
- [x] CRUD de candidatos por elección y cargo
- [ ] Vista previa del tarjetón

**Bloque 2 — Flujo de votación**
- [ ] Verificación biométrica con UI (pantalla del día D)
- [ ] Verificación por escáner 2D (fallback)
- [ ] Pantalla de confirmación de identidad con foto
- [ ] Validación de voto previo (consulta REGISTRO_VOTOS)
- [ ] Tarjetón de votación con candidatos + voto en blanco
- [ ] Modal de confirmación irreversible
- [ ] Registro atómico via sp_registrar_voto

**Bloque 3 — Gestión de votantes**
- [ ] Listado con filtros (rol, puesto, estado)
- [ ] Edición de datos personales + log en AUDITORIA_VOTANTES
- [ ] Cambio de rol y puesto
- [ ] Inhabilitar / habilitar votante
- [x] Re-enrolamiento biométrico (usa BiometriaVotanteRepository con desactivar() + save())

---

## 12. Casos de uso especiales conocidos

| Situación | Solución implementada |
|---|---|
| Votante pierde el dedo registrado | Re-enrolamiento (admin valida con foto + cédula, borra plantilla, nuevo enrolamiento con otro dedo, log en AUDITORIA_VOTANTES) |
| Datos mal extraídos por OCR | Edición manual de datos personales del votante por el admin |
| Sensor biométrico falla en jornada | Fallback a escáner 2D: lee PDF417, extrae IDENTIFICACION, muestra foto para confirmación visual |
| Votante intenta votar dos veces | Consulta REGISTRO_VOTOS antes del tarjetón — si existe bloquea con "Voto ya ejercido" |
| Votante quiere voto en blanco | Pendiente de definir — la FK compuesta en VOTOS impide ID_CANDIDATO NULL. Opciones: A) Candidato especial "VOTO EN BLANCO" por cargo en cada elección, B) Tabla separada VOTOS_BLANCO. Decisión requerida antes de implementar el tarjetón (Bloque 2) |
| Error técnico durante registro de voto | sp_registrar_voto hace ROLLBACK automático — el votante puede reintentar |

---

## 13. Scripts de inicio

```bash
# Iniciar todos los servicios
INICIAR_SERVICIOS.bat

# Detener todos los servicios
DETENER_SERVICIOS.bat

# Schema inicial de BD
STARTUP.SQL
```

---

*Última actualización: Mayo 2026*
