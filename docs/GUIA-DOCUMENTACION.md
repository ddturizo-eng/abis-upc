# Guia de Documentacion — ABIS-UPC

Esta guia define el estandar de documentacion para todos los modulos del sistema. Cada desarrollador debe seguir las convenciones de su lenguaje al documentar codigo, y la estructura definida aqui al escribir archivos en `docs/`.

---

## Principio general

**Un comentario profesional explica el *porque*, no el *que*.**

El codigo ya dice que hace. El comentario dice por que se tomo esa decision, que restriccion de hardware o ley la justifica, o que caso borde debe considerarse.

---

## 1. Comentarios en codigo — por lenguaje

### Java (backend principal — Javalin)

Estandar **Javadoc** en todas las clases publicas, metodos publicos y constantes con significado de dominio.

**Clase:**

```java
/**
 * Servicio de cifrado para plantillas biometricas.
 *
 * <p>Utiliza AES-256-CBC con un KeyStore JCEKS para garantizar que las
 * huellas dactilares nunca se almacenen en texto plano. La clave se carga
 * una sola vez en el constructor y se mantiene en memoria — no se serializa.
 *
 * <p>Cumple con el articulo 17 de la Ley 1581/2012 (medidas de seguridad
 * en tratamiento de datos sensibles).
 */
public class CryptoService { ... }
```

**Metodo:**

```java
/**
 * Registra el voto de un votante de forma atomica.
 *
 * <p>Invoca el stored procedure {@code prc_registrar_voto}, que ejecuta dos
 * INSERT en una sola transaccion ACID: uno en VOTOS (sin identificacion) y
 * otro en REGISTRO_VOTOS (sin candidato). Este diseno garantiza anonimato
 * irreversible incluso con acceso directo a la base de datos.
 *
 * @param votanteId   identificacion del votante autenticado
 * @param candidatoId identificador del candidato seleccionado
 * @throws VotoYaRegistradoException si el votante ya ejercio su derecho
 *         en esta jornada (controlado por trigger)
 */
public void registrarVoto(String votanteId, int candidatoId) { ... }
```

**Controller:**

```java
/**
 * GET /api/elecciones/{id}/elegibilidad
 *
 * <p>Retorna el desglose de votantes elegibles por rol para una eleccion
 * especifica. Cruza ELECCION_ROLES con VOTANTES para mostrar totales,
 * pendientes y ejercidos. Los ejercidos se cuentan desde REGISTRO_VOTOS,
 * no desde ESTADO_VOTO (porque el voto es per-eleccion, no global).
 *
 * <p>Usado por el panel de administracion para validar cobertura antes
 * de iniciar una jornada.
 */
public static void elegibilidad(Context ctx) { ... }
```

**Reglas adicionales para Java:**

- Usa `//` solo para comentarios de una linea que expliquen una decision no obvia
- No comentes getters, setters ni codigo autoexplicativo

```java
// MAL — describe lo obvio
// Obtiene el votante por ID
Votante v = votanteRepo.findById(id);

// BIEN — explica la decision de negocio
// Se lanza excepcion en lugar de retornar null: un votante inexistente
// en este punto es un estado invalido del sistema, no un caso esperado.
Votante v = votanteRepo.findById(id)
    .orElseThrow(() -> new VotanteNoEncontradoException(id));
```

---

### Python (FastAPI — biometrico y OCR)

Estandar **docstrings con formato Google Style**.

**Funcion:**

```python
def enroll_fingerprint(samples: list[bytes], votante_id: str) -> str:
    """Enrola la huella dactilar de un votante generando una plantilla cifrada.

    Requiere exactamente 4 muestras del mismo dedo para garantizar calidad
    minima de registro (umbral NIST: score >= 40). Si alguna muestra no
    supera el umbral, se rechaza todo el lote — nunca se persiste una
    plantilla degradada.

    Args:
        samples: Lista de 4 imagenes en bytes capturadas por DigitalPersona.
        votante_id: Identificacion del votante, usada como contexto de cifrado.

    Returns:
        Plantilla biometrica cifrada en base64 (AES-256-CBC).

    Raises:
        InsufficientQualityError: Si alguna muestra no supera el umbral de calidad.
        EnrollmentError: Si el SDK de DigitalPersona falla durante la extraccion.
    """
```

**Endpoint FastAPI:**

```python
@router.post("/verify", summary="Verificar identidad por huella")
async def verify(request: VerifyRequest):
    """
    Compara la huella capturada contra la plantilla almacenada.

    Retorna un score de similitud (0-100). El umbral operacional
    de ABIS-UPC es 60, configurable via variable de entorno
    ABIS_MATCH_THRESHOLD.
    """
```

El docstring alimenta automaticamente la documentacion Swagger en `/docs`.

**Modulo (archivo `__init__.py`):**

```python
"""
Motor de asignacion de jurados.

Este modulo implementa el algoritmo de distribucion aleatoria con
restricciones de rol (RN-01), edad (RN-02), exclusion de candidatos
(RN-03), no duplicado (RN-04) y preferencia de administrativos (RN-07).
"""
```

**Reglas adicionales para Python:**

- Type hints en TODAS las funciones publicas
- Usa `#` solo para explicar pasos no triviales dentro de una funcion
- No comentes lineas que ya son obvias por su nombre

```python
# MAL — describe lo obvio
data = request.json()  # obtiene el JSON del request

# BIEN — explica el por que
# Se fuerza str() porque el OCR puede retornar el numero como int
identificacion = str(data.get("numero", ""))
```

---

### PL/SQL (Oracle — stored procedures y triggers)

Estandar **bloque de comentario en encabezado** con proposito, parametros, diseño y excepciones.

**Stored procedure:**

```sql
/**
 * PRC_REGISTRAR_VOTO
 * ------------------
 * Proposito: Registrar un sufragio garantizando anonimato e integridad.
 *
 * Diseno de anonimato:
 *   - VOTOS recibe ID_CANDIDATO pero NO IDENTIFICACION.
 *   - REGISTRO_VOTOS recibe IDENTIFICACION pero NO ID_CANDIDATO.
 *   Es imposible cruzar ambas tablas para conocer el voto de una persona.
 *
 * Transaccionalidad:
 *   Ambos INSERT ocurren en una sola transaccion. Si cualquiera falla,
 *   se ejecuta ROLLBACK automatico — no existe estado parcial.
 *
 * Parametros:
 *   p_identificacion  IN  VARCHAR2  -- Cedula del votante autenticado
 *   p_id_candidato    IN  NUMBER    -- PK del candidato seleccionado
 *   p_id_eleccion     IN  NUMBER    -- Jornada electoral activa
 *   p_id_puesto       IN  NUMBER    -- Puesto donde se emite el voto
 *
 * Excepciones:
 *   -20070  Votante no habilitado para esta eleccion
 *   -20071  Candidato invalido o nulo
 */
CREATE OR REPLACE PROCEDURE prc_registrar_voto(
    p_identificacion IN VARCHAR2,
    p_id_eleccion    IN NUMBER,
    p_id_candidato   IN NUMBER,
    p_id_puesto      IN NUMBER
) AS ...
```

**Trigger:**

```sql
/**
 * TRG_JURADO_NO_DUPLICADO
 * ------------------------
 * Dispara: BEFORE INSERT ON JURADOS
 *
 * Proposito: Validar RN-04 — un votante no puede ser jurado en dos mesas
 *            de la misma eleccion.
 *
 * Logica:
 *   1. Obtiene el ID_ELECCION de la mesa a la que se asigna.
 *   2. Busca si el votante ya esta en otra mesa de ESA eleccion.
 *   3. Si existe -> ORA-20080 y rechaza el INSERT.
 */
CREATE OR REPLACE TRIGGER trg_jurado_no_duplicado
BEFORE INSERT ON JURADOS
FOR EACH ROW ...
```

**Reglas adicionales para PL/SQL:**

- Un comentario `--` por cada paso numerado si la logica tiene +3 pasos
- Documentar el codigo de error ORA en el encabezado
- Si el SP tiene COMMIT/ROLLBACK, explicar por que en ese punto exacto

---

### C# (NativeService — WebSocket con DigitalPersona)

Estandar **XML Documentation Comments**.

```csharp
/// <summary>
/// Servidor WebSocket que actua como puente entre el SDK nativo de
/// DigitalPersona (COM/interop) y el microservicio Python en :8001.
/// </summary>
/// <remarks>
/// DigitalPersona solo expone su SDK en .NET Framework 4.8 — no existe
/// wrapper para Python ni Java. Este servicio existe exclusivamente por
/// esa restriccion de hardware. Expone un unico endpoint WS en :8765.
/// </remarks>
public class NativeService { ... }

/// <summary>Captura una muestra de huella del lector activo.</summary>
/// <returns>Imagen en bytes formato BMP sin compresion.</returns>
/// <exception cref="ReaderNotConnectedException">
/// Si el lector DigitalPersona no esta conectado al momento de la captura.
/// El frontend debe manejar este caso mostrando el flujo de contingencia QR.
/// </exception>
public byte[] CaptureSample() { ... }
```

---

### JavaScript / Node.js (email-service + frontend)

Estandar **JSDoc** para funciones publicas.

```javascript
/**
 * Genera y envia el certificado de votacion al correo del votante.
 *
 * El envio es no bloqueante — se despacha como tarea asincrona despues
 * de confirmar el registro del voto, para no anadir latencia al flujo
 * de votacion. Si falla, queda registrado en AUDITORIA_CORREOS con estado
 * PENDIENTE para reintento manual desde el panel de administracion.
 *
 * @param {string} identificacion - Cedula del votante
 * @param {string} eleccionNombre - Nombre de la jornada electoral
 * @param {string} correo - Destino del certificado
 * @returns {Promise<{messageId: string}>} ID del mensaje enviado por Resend
 * @throws {EmailServiceError} Si Resend rechaza el envio tras 3 reintentos
 */
async function enviarCertificado(identificacion, eleccionNombre, correo) { ... }
```

**Frontend (vanilla JS):**

```javascript
/**
 * Abre el drawer de edicion de votante.
 *
 * Carga dinamicamente los puestos de votacion desde /api/puestos al
 * entrar en modo edicion. Si el endpoint falla, usa una lista vacia —
 * el administrador puede guardar el puesto actual sin cambiarlo.
 *
 * @param {Object} voter - Objeto votante desde GET /api/votantes
 * @param {string} action - 'details', 'edit', 'bio', o 'audit'
 */
function openDrawer(voter, action) { ... }
```

---

## 2. Nombres que no necesitan comentario

```java
// MAL
boolean flag = verificar(id, fp);

// BIEN — el nombre elimina el comentario
boolean identidadVerificada = servicioHuella.verificar(votanteId, muestraHuella);
```

```python
# MAL
def proc(d): ...

# BIEN
def extraer_datos_cedula(imagen: bytes) -> DatosCedula: ...
```

---

## 3. Una funcion, una responsabilidad

```python
# MAL — hace OCR, valida, guarda y envia correo
def procesar_documento(imagen): ...

# BIEN — separado por responsabilidad
def extraer_datos_cedula(imagen: bytes) -> DatosCedula: ...
def validar_contra_censo(datos: DatosCedula) -> bool: ...
def persistir_enrolamiento(datos: DatosCedula, huella: str) -> None: ...
```

---

## 4. Constantes nombradas sobre numeros magicos

```java
// MAL
if (score >= 60) { ... }

// BIEN
private static final int UMBRAL_VERIFICACION_BIOMETRICA = 60;
if (score >= UMBRAL_VERIFICACION_BIOMETRICA) { ... }
```

```javascript
// MAL
if (coverage < 50) { ... }

// BIEN
const COBERTURA_MINIMA_VIABLE = 50;
if (coverage < COBERTURA_MINIMA_VIABLE) { ... }
```

---

## 5. Estructura de documentacion en docs/

Cada desarrollador produce los archivos de su parte dentro de `docs/`:

```
docs/
├── arquitectura/
│   ├── C4-contexto.md          # Diagrama nivel 1: actores externos
│   ├── C4-contenedores.md      # Diagrama nivel 2: los 6 servicios
│   └── decisiones/
│       ├── ADR-001-oracle-sobre-postgres.md
│       ├── ADR-002-javalin-sobre-spring.md
│       └── ADR-003-anonimato-dos-tablas.md
├── base-de-datos/
│   ├── modelo-entidad-relacion.md
│   └── reglas-de-negocio.md
├── api/
│   └── endpoints.md
├── frontend/
│   ├── flujo-registro.md
│   └── flujo-votacion.md
└── despliegue/
    └── guia-instalacion.md
```

---

## 6. Plantilla de ADR (Architecture Decision Record)

```markdown
# ADR-00X: Titulo breve de la decision

**Estado:** Aceptado
**Fecha:** DD/MM/2026
**Decisores:** [nombre]

## Contexto
[Que problema o necesidad motiva esta decision]

## Decision
[Que decidimos hacer]

## Alternativas consideradas
1. [Opcion A] — [por que se descarto]
2. [Opcion B] — [por que se descarto]

## Consecuencias
- Positivo: [que ganamos]
- Negativo: [que sacrificamos o que riesgo asumimos]
```

---

## 7. Asignacion de partes

| Parte | Responsable | Rama |
|---|---|---|
| **Parte 1** — Backend Java + BD + PL/SQL | Jorge Herrera | `feature/documentacion` |
| **Parte 2** — Frontend + flujos de usuario + CSS | Daniel Florez | `feature/documentacion` |
| **Parte 3** — Microservicios + despliegue + scripts | Mateo Calderon | `feature/documentacion` |

### Parte 1 — Backend Java + Base de Datos

- Arquitectura C4, modelo hibrido, capas
- Esquema Oracle XE, tablas, principio de anonimato
- PL/SQL: stored procedures, funciones, packages, triggers
- AppServer, AppConfig, estructura de paquetes
- Controllers: documentar cada endpoint (ruta, metodo, parametros, respuesta)
- Services: VotacionService, AdminService, ContingenciaTokenService, CertificadoService, ResultadosService, EleccionLifecycleService
- Repositories: patron repository, VotanteRepository, VotoOracleRepository, etc.
- Seguridad: AuthMiddleware, CryptoService, HashingService, KeyStoreManager
- Integracion: CertificadoClient, ContingenciaEmailClient, QrRenderClient, BiometricClient
- Utilidades: OracleErrorHandler

### Parte 2 — Frontend + Flujos de Usuario

- Estructura del frontend: pages, js, styles, components, assets
- Panel admin: admin-shell.js, header, navegacion, dashboard.js
- Modulos admin: Votantes, Elecciones, Candidatos, Jurados, Jornada, Contingencia, Certificados
- Registro (4 pasos): router.js, OCR, foto, biometria, verificacion
- Votacion (kiosco): bienvenida, verificacion, identidad, tarjeton, confirmacion
- Sistema de toasts, CSS institucional, paleta de colores

### Parte 3 — Microservicios + Despliegue

- abis-biometric (FastAPI :8001): enroll, verify, vote, qr, native_client
- abis-ocr (FastAPI :8002): pipeline, motores, parsers, clasificacion
- abis-email-service (Node.js :8010): emailService, pdfService, certificadoService, contingenciaService
- abis-native (C# :8765): WebSocket DigitalPersona
- Motor de jurados: wizard, reglas RN-01 a RN-07, algoritmo de asignacion
- Instalacion: INICIAR_SERVICIOS.bat, DETENER_SERVICIOS.bat, .env.example, STARTUP.SQL
- Scripts auxiliares: probar_scanner.py, contingencia_scanner.py, test-backend-smoke.ps1
