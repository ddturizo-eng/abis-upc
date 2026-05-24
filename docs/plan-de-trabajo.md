# PLAN DE TRABAJO — ABIS-UPC 
## Sistema Automatizado de Identificación Biométrica
## Universidad Popular del Cesar | Programación III | 2026
## Tech Lead: Ing. Daniel Turizo
## Última actualización: 21 de abril 2026

---

## 1. VISIÓN GENERAL

### ¿Qué se está construyendo?
Sistema biométrico electoral distribuido que valida la identidad del votante mediante huella dactilar (Digital Persona), captura facial con liveness detection (MediaPipe), y lectura de documentos de identidad (OCR + escáner 2D opcional). Garantiza el secreto del sufragio mediante separación arquitectónica de datos y cumple la Ley 1581/2012.

### Estado actual del proyecto
- Base de datos Oracle: **11 tablas creadas, PKs asignadas, sequences creadas**
- FKs y constraints UNIQUE: **pendientes** (se aplican cuando el profesor explique)
- Código Java: **estructura de paquetes existente, lógica pendiente**
- Python: **main.py funcional con OCR básico, refactorización pendiente**
- Frontend: **páginas base creadas, lógica pendiente**

### Stack tecnológico definitivo

| Capa | Tecnología | Versión |
|---|---|---|
| Backend Java | Javalin 6 | Java 21 |
| Microservicio Python | FastAPI | 3.11 |
| Base de datos | Oracle XE | 21c |
| Frontend | Vanilla JS + HTML/CSS | — |
| Pool conexiones | HikariCP | 5.1.0 |
| Visión artificial | OpenCV | 4.10.0 |
| OCR | Tesseract | 5.x |
| Captura facial | MediaPipe FaceMesh | 0.10.x |
| Sensor huella | Digital Persona | USB HID |
| Escáner 2D | YHD-9300 | Probable compra |
| Cifrado | JCA AES-256 + SHA-256 | Java stdlib |
| Notificaciones | SendGrid | — |
| PDF | iText / Apache PDFBox | TBD |

---

## 2. ARQUITECTURA DEL SISTEMA

### Capas

```
┌──────────────────────────────────────────────────────────────┐
│  PRESENTACIÓN — abis-frontend (Vanilla JS + HTML/CSS)        │
│  Kiosco votación · Panel pre-registro · Panel admin          │
└───────────────────────────┬──────────────────────────────────┘
                            │ HTTP fetch JSON
┌───────────────────────────▼──────────────────────────────────┐
│  APLICACIÓN — abis-backend (Java 21 + Javalin :7000)         │
│  Controller → Service → Repository → Oracle                  │
│  Security: KeyStore + AES-256 + SHA-256                      │
└─────────────┬────────────────────────┬───────────────────────┘
              │ HikariCP               │ HTTP Unirest
     ┌────────▼────────┐    ┌──────────▼──────────────────────┐
     │  Oracle XE      │    │  abis-biometric (Python :8000)  │
     │  :1521          │    │  OCR · Digital Persona           │
     │  11 tablas       │    │  MediaPipe · YHD-9300 (opcional)│
     └─────────────────┘    └─────────────────────────────────┘
```

### Hardware biométrico

| Dispositivo | Conexión | Uso | Estado |
|---|---|---|---|
| Digital Persona | USB HID | Captura y verificación de huella | Confirmado |
| Webcam HD | USB | OCR documento + liveness facial | Existente |
| YHD-9300 | USB RS232 | PDF417/QR cédulas colombianas | Probable compra |

---

## 3. EQUIPO Y RESPONSABILIDADES

| Ingeniero | Rol | Rama principal |
|---|---|---|
| Daniel Turizo | Tech Lead + Full Stack | `develop`, `feature/biometric`, `feature/services` |
| Daniel Flórez | Backend Security | `feature/security` |
| Mateo Calderón | Backend DAO | `feature/dao-oracle` |
| Jorge Herrera | Backend DAO | `feature/dao-oracle` |
| Ana Laura Cuéllar | Frontend | `feature/frontend` |

**Regla inmutable:** Solo Daniel Turizo hace merge a `develop` y `main`.

---

## 4. ETAPAS DE DESARROLLO

---

### ETAPA 0 — Preparación y entorno base
**Fechas:** 12–18 abril (ya en curso)
**Responsable:** Daniel Turizo
**Objetivo:** Todo el equipo puede arrancar a codificar sin bloqueos de entorno.

#### E0-A01: Configurar variables de entorno y seguridad del repositorio
**Qué hacer:** Crear `.env.example` en la raíz con todas las variables requeridas. Actualizar `.gitignore`.
**Cómo hacerlo:** Crear el archivo manualmente con las siguientes entradas vacías.
**Entregable:** `.env.example` y `.gitignore` actualizados en `develop`.
**Variables:**
```
ABIS_DB_URL=
ABIS_DB_PASSWORD=
ABIS_KEYSTORE_PATH=
ABIS_KEYSTORE_PASSWORD=
ABIS_AES_KEY_ALIAS=
ABIS_SENDGRID_API_KEY=
```
**`.gitignore` debe incluir:**
```
*.jks
*.p12
.env
venv/
target/
__pycache__/
*.pyc
```
**Git:**
```
git checkout develop
git pull origin develop
# Crear archivos
git add .env.example .gitignore
git commit -m "chore: agregar .env.example y actualizar .gitignore"
git push origin develop
```

#### E0-A02: Crear estructura de paquetes Java vacía en feature/dao-oracle
**Qué hacer:** Crear archivos Java con estructura de paquetes correcta pero sin lógica. Solo la declaración de clase/interfaz.
**Responsable:** Daniel Turizo (hace el scaffold para que Mateo y Jorge sepan exactamente qué crear)
**Archivos a crear en `com.abisupc`:**

```
model/
  Entity.java          ← clase abstracta con id : Long
  Votante.java         ← campos según tabla VOTANTES
  Rol.java             ← campos ID_ROL, NOMBRE, PESO_VOTO
  PuestoVotacion.java  ← campos ID_PUESTOS, CIUDAD, SEDE, NOMBRE_PUESTO
  MesaJurado.java      ← campos ID_MESA, HORA_INGRESO, HORA_SALIDA, CARGO, PUESTOS_VOTACION_IDPUESTOS
  Jurado.java          ← PK compuesta: MESA_JURADOS_IDMESA + VOTANTES_IDENTIFICACION
  Administrador.java   ← campos ID_ADMIN, USUARIO, PASSWORD_HASH, NOMBRE, CORREO
  Sesion.java          ← campos ID_SESION, TOKEN, FECHA_INICIO, ADMINISTRADORES_IDADMIN, FECHA_FIN
  Eleccion.java        ← campos ID_ELECCION, NOMBRE, FECHAHORA_INICIO, FECHAHORA_FIN, ESTADO
  Candidato.java       ← campos ID_CANDIDATO, nombres, NUMERO_CAMPANIA, CARGO, ELECCIONES_IDELECCION
  RegistroVoto.java    ← campos ID_REGISTRO, FECHA_HORA, VOTANTES_IDENTIFICACION, PUESTOS_VOTACION_IDPUESTOS, ELECCIONES_IDELECCION
  Voto.java            ← campos ID_VOTOS, ROLES_IDROL, ELECCIONES_IDELECCION, IDCANDIDATO, FECHA_HORA, PESOVOTO_APLICADO
  EstadoEleccion.java  ← enum: PROGRAMADA, EN_CURSO, CERRADA
  EstadoVotante.java   ← enum: PENDIENTE, EJERCIDO, INHABILITADO
repository/
  Repository.java        ← interface genérica T
  VotanteRepository.java
  RolRepository.java
  PuestoVotacionRepository.java
  MesaJuradoRepository.java
  JuradoRepository.java
  AdministradorRepository.java
  SesionRepository.java
  EleccionRepository.java
  CandidatoRepository.java
  RegistroVotoRepository.java
  VotoRepository.java
```

**Git (en feature/dao-oracle):**
```
git checkout feature/dao-oracle
git merge develop
# Crear archivos vacíos
git commit -m "chore: scaffold de entidades y repositorios según UML v6"
git push origin feature/dao-oracle
```

#### E0-A03: README de setup del proyecto
**Qué hacer:** Crear `README.md` en la raíz con instrucciones de setup para que cualquier desarrollador nuevo pueda arrancar.
**Contenido mínimo:** Requisitos (Java 21, Python 3.11, Oracle XE), instrucciones de configuración de variables de entorno, cómo generar el `.jks`, cómo correr el backend Java y el microservicio Python.

---

### ETAPA 1 — Capa de datos e infraestructura
**Fechas:** 15–25 abril
**Objetivo:** Entidades Java + repositorios funcionales contra Oracle real. Capa de seguridad JCA completa.
**Depende de:** E0 completa

---

#### E1-A01: Completar DDL Oracle — FKs y constraints
**Responsable:** Todo el equipo (sesión conjunta cuando el profesor explique FKs)
**Qué hacer:** Ejecutar los ALTER TABLE de foreign keys y el UNIQUE correcto en REGISTRO_VOTOS.
**Script a ejecutar (cuando el profesor lo autorice):**
```sql
-- FK: VOTANTES → ROLES
ALTER TABLE VOTANTES ADD CONSTRAINT fk_votantes_roles
    FOREIGN KEY (ROLES_IDROL) REFERENCES ROLES(ID_ROL);

-- FK: VOTANTES → PUESTOS_VOTACION
ALTER TABLE VOTANTES ADD CONSTRAINT fk_votantes_puestos
    FOREIGN KEY (PUESTOS_VOTACION_IDPUESTOS) REFERENCES PUESTOS_VOTACION(ID_PUESTOS);

-- FK: MESA_JURADOS → PUESTOS_VOTACION
ALTER TABLE MESA_JURADOS ADD CONSTRAINT fk_mesa_puestos
    FOREIGN KEY (PUESTOS_VOTACION_IDPUESTOS) REFERENCES PUESTOS_VOTACION(ID_PUESTOS);

-- FK: JURADOS → MESA_JURADOS
ALTER TABLE JURADOS ADD CONSTRAINT fk_jurados_mesa
    FOREIGN KEY (MESA_JURADOS_IDMESA) REFERENCES MESA_JURADOS(ID_MESA);

-- FK: JURADOS → VOTANTES
ALTER TABLE JURADOS ADD CONSTRAINT fk_jurados_votantes
    FOREIGN KEY (VOTANTES_IDENTIFICACION) REFERENCES VOTANTES(IDENTIFICACION);

-- FK: CANDIDATOS → ELECCIONES
ALTER TABLE CANDIDATOS ADD CONSTRAINT fk_candidatos_elecciones
    FOREIGN KEY (ELECCIONES_IDELECCION) REFERENCES ELECCIONES(ID_ELECCION);

-- FK: VOTOS → ROLES
ALTER TABLE VOTOS ADD CONSTRAINT fk_votos_roles
    FOREIGN KEY (ROLES_IDROL) REFERENCES ROLES(ID_ROL);

-- FK: VOTOS → ELECCIONES
ALTER TABLE VOTOS ADD CONSTRAINT fk_votos_elecciones
    FOREIGN KEY (ELECCIONES_IDELECCION) REFERENCES ELECCIONES(ID_ELECCION);

-- FK: VOTOS → CANDIDATOS
ALTER TABLE VOTOS ADD CONSTRAINT fk_votos_candidatos
    FOREIGN KEY (IDCANDIDATO) REFERENCES CANDIDATOS(ID_CANDIDATO);

-- FK: REGISTRO_VOTOS → VOTANTES
ALTER TABLE REGISTRO_VOTOS ADD CONSTRAINT fk_registro_votantes
    FOREIGN KEY (VOTANTES_IDENTIFICACION) REFERENCES VOTANTES(IDENTIFICACION);

-- FK: REGISTRO_VOTOS → PUESTOS_VOTACION
ALTER TABLE REGISTRO_VOTOS ADD CONSTRAINT fk_registro_puestos
    FOREIGN KEY (PUESTOS_VOTACION_IDPUESTOS) REFERENCES PUESTOS_VOTACION(ID_PUESTOS);

-- FK: REGISTRO_VOTOS → ELECCIONES
ALTER TABLE REGISTRO_VOTOS ADD CONSTRAINT fk_registro_elecciones
    FOREIGN KEY (ELECCIONES_IDELECCION) REFERENCES ELECCIONES(ID_ELECCION);

-- UNIQUE: un votante vota UNA VEZ por elección (no en todo el sistema)
ALTER TABLE REGISTRO_VOTOS ADD CONSTRAINT uq_votante_por_eleccion
    UNIQUE (VOTANTES_IDENTIFICACION, ELECCIONES_IDELECCION);

-- FK: SESIONES → ADMINISTRADORES
ALTER TABLE SESIONES ADD CONSTRAINT fk_sesiones_admin
    FOREIGN KEY (ADMINISTRADORES_IDADMIN) REFERENCES ADMINISTRADORES(ID_ADMIN);
```
**Entregable:** Script `abis-database/ddl/02_foreign_keys.sql` con todas las FKs.
**Criterio de aceptación:** Todas las FKs creadas sin error en SQL Developer.

---

#### E1-A02: DML — Datos maestros iniciales
**Responsable:** Jorge Herrera + Mateo Calderón
**Qué hacer:** Insertar datos iniciales en ROLES y PUESTOS_VOTACION.
**Archivo:** `abis-database/dml/datos_maestros.sql`
```sql
-- Roles con peso de voto según reglamento
INSERT INTO ROLES VALUES (seq_roles.NEXTVAL, 'ESTUDIANTE', 1.00);
INSERT INTO ROLES VALUES (seq_roles.NEXTVAL, 'DOCENTE', 2.00);
INSERT INTO ROLES VALUES (seq_roles.NEXTVAL, 'ADMINISTRATIVO', 1.50);
INSERT INTO ROLES VALUES (seq_roles.NEXTVAL, 'EGRESADO', 0.50);
COMMIT;

-- Puestos de votación UPC
INSERT INTO PUESTOS_VOTACION VALUES (seq_puestos_votacion.NEXTVAL, 'Valledupar', 'Sede Sabanas', 'Sala de Sistemas 204');
INSERT INTO PUESTOS_VOTACION VALUES (seq_puestos_votacion.NEXTVAL, 'Valledupar', 'Sede Hurtado', 'Auditorio Principal');
COMMIT;
```
**Git:**
```
git checkout feature/dao-oracle
git add abis-database/dml/datos_maestros.sql
git commit -m "feat: insertar datos maestros roles y puestos votacion"
git push origin feature/dao-oracle
```

---

#### E1-A03: Implementar entidades del dominio — Mateo
**Responsable:** Mateo Calderón
**Rama:** `feature/dao-oracle`
**Qué hacer:** Implementar getters, setters y constructores de las entidades asignadas.
**Referencia UML:** `docs/uml-clases-backend-java.md` → sección CAPA DE DOMINIO
**Clases a implementar:**
- `Votante.java` — campos según tabla VOTANTES. `estadoVoto` es String ('PENDIENTE','EJERCIDO','INHABILITADO')
- `Eleccion.java` — campos ID_ELECCION, NOMBRE, FECHAHORA_INICIO, FECHAHORA_FIN, ESTADO
- `Candidato.java` — campos ID_CANDIDATO, nombres, NUMERO_CAMPANIA, CARGO, ELECCIONES_IDELECCION. Sin idCargo.
- `RegistroVoto.java` — campos con nombres que mapean exactamente a las columnas Oracle
- `Voto.java` — incluir PESOVOTO_APLICADO como double

**Convención crítica:** Los campos Java se llaman en camelCase pero los SQL en los repos usan el nombre Oracle exacto.
```java
// CORRECTO — así se mapea en el Repository
rs.getString("PRIMER_NOMBRE")  // Oracle
votante.getPrimerNombre()        // Java
```
**Criterio de aceptación:** Todas las clases compilan. Ningún campo hardcodeado. Getters/setters para todos los atributos.

---

#### E1-A04: Implementar entidades del dominio — Jorge
**Responsable:** Jorge Herrera
**Rama:** `feature/dao-oracle`
**Clases a implementar:**
- `Entity.java` — clase abstracta con `id : Long`, getId(), setId()
- `Rol.java` — incluir `pesoVoto : double`
- `PuestoVotacion.java`
- `MesaJurado.java`
- `Jurado.java` — PK compuesta: idMesa + identificacion. No tiene ID propio.
- `Administrador.java`
- `Sesion.java` — fechaFin puede ser null (sesión activa)
- `EstadoEleccion.java` (enum: PROGRAMADA, EN_CURSO, CERRADA)
- `EstadoVotante.java` (enum: PENDIENTE, EJERCIDO, INHABILITADO)
- `Repository.java` (interfaz genérica)
- `AppConfig.java` — HikariCP con variables de entorno

---

#### E1-A05: Implementar repositorios — Mateo
**Responsable:** Mateo Calderón
**Rama:** `feature/dao-oracle`
**Clases:** `VotanteRepository`, `EleccionRepository`, `CandidatoRepository`, `RegistroVotoRepository`, `VotoRepository`

**Patrón obligatorio para todos:**
```java
// PreparedStatement SIEMPRE — nunca concatenación
String sql = "SELECT * FROM VOTANTES WHERE IDENTIFICACION = ?";
try (PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setString(1, identificacion);
    ResultSet rs = ps.executeQuery();
    // mapear...
}
```

**VotoRepository** necesita métodos adicionales:
- `obtenerResultadosPonderados(idEleccion)`: `SELECT IDCANDIDATO, SUM(PESOVOTO_APLICADO) FROM VOTOS WHERE ELECCIONES_IDELECCION=? GROUP BY IDCANDIDATO`
- `obtenerResultadosPorRol(idEleccion)`: JOIN con ROLES para estadísticas por estamento

---

#### E1-A06: Implementar repositorios — Jorge
**Responsable:** Jorge Herrera
**Rama:** `feature/dao-oracle`
**Clases:** `RolRepository`, `PuestoVotacionRepository`, `MesaJuradoRepository`, `JuradoRepository`, `AdministradorRepository`, `SesionRepository`

**JuradoRepository** es especial por la PK compuesta:
```java
// save() usa los dos campos de la PK compuesta
String sql = "INSERT INTO JURADOS (MESA_JURADOS_IDMESA, VOTANTES_IDENTIFICACION, FECHA_ASIGNACION, CARGO) VALUES (?,?,?,?)";
// findById() recibe un objeto Jurado con idMesa + identificacion como identificadores
// esJurado() hace SELECT COUNT(*) WHERE VOTANTES_IDENTIFICACION = ?
```

---

#### E1-A07: Implementar capa de seguridad JCA
**Responsable:** Daniel Flórez
**Rama:** `feature/security`
**Referencia:** `docs/uml-security.md`
**Clases:** `KeyStoreManager`, `CryptoService`, `HashingService`, `AuthMiddleware`

**Orden de implementación:**
1. `KeyStoreManager` primero — las otras dos dependen de él
2. `CryptoService` — usa `KeyStoreManager.getAesKey()`
3. `HashingService` — independiente, solo `MessageDigest`
4. `AuthMiddleware` — requiere `SesionRepository` (mockeado para tests)

**Tests unitarios obligatorios:**
```java
// CryptoService
@Test void cifrarDescifrar_retornaOriginal()
@Test void descifrarConLlaveErronea_lanzaExcepcion()

// HashingService
@Test void hashVerificar_coincide()
@Test void hashAlterado_noCoincide()
@Test void verificarIntegridad_templateAlterado_retornaFalse()
```

---

### ETAPA 2 — Microservicio Python biométrico
**Fechas:** 19 abril – 2 mayo (paralelo con E1)
**Responsable:** Daniel Turizo
**Rama:** `feature/biometric`
**Objetivo:** FastAPI modular con OCR pipeline, Digital Persona, MediaPipe liveness.

---

#### E2-A01: Refactorizar main.py a estructura modular
**Qué hacer:** Mover el código del `main.py` monolítico a la estructura de carpetas definida en el UML Python v2.
**Por qué:** El código actual viola SRP. Todo en un archivo = imposible de testear por partes.
**Entregable:**
```
app/
  main.py              ← solo: FastAPI(), include_router() x4, CORS
  core/config.py       ← leer variables de entorno con os.getenv()
  routers/ocr_router.py
  routers/scanner_router.py
  routers/fingerprint_router.py
  routers/face_router.py
  services/document_classifier.py
  services/roi_extractor.py
  services/ocr_engine.py
  services/document_parser.py
  services/scanner_service.py
  services/fingerprint_service.py
  services/face_service.py
  schemas/ocr_schema.py
  schemas/fingerprint_schema.py
  schemas/face_schema.py
```
**Git:**
```
git checkout feature/biometric
git commit -m "refactor: separar main.py en routers, services y schemas"
```

---

#### E2-A02: Implementar pipeline OCR mejorado
**Qué hacer:** Implementar los 4 servicios del pipeline OCR con ROIs específicas por tipo de documento.
**Por qué:** OCR sobre imagen completa = 20% eficacia. OCR sobre ROI recortada = ~70%.
**Servicios a implementar:**
- `document_classifier.py`: analizar colores + proporción → tipo de documento
- `roi_extractor.py`: coordenadas (x,y,w,h) por tipo y campo
- `ocr_engine.py`: Tesseract con psm 7 (número) y psm 6 (nombre)
- `document_parser.py`: regex de corrección de errores OCR

**Tipos de documento soportados:** CC_ANTIGUA, CC_DIGITAL, TI, CARNET_UPC
**Criterio de aceptación:** Con 3 cédulas reales, precisión ≥ 60% en número de identificación.

---

#### E2-A03: Integrar Digital Persona (USB HID)
**Qué hacer:** Implementar `fingerprint_service.py` usando el SDK de Digital Persona.
**Dependencia a instalar:** `dpfj` (Windows) o `libfprint` (Linux)
```
pip install dpfj
```
**Implementar:**
- `inicializar_sdk()`: conectar al reader DP
- `capturar_imagen()`: obtener imagen raw del sensor
- `extraer_template()`: convertir imagen a template ISO
- `comparar_templates()`: retornar score 0-100
- `obtener_estado()`: verificar si el reader está conectado

**Criterio de aceptación:** Enrolamiento y verificación funcionales con el sensor físico.
**Git:**
```
git commit -m "feat: integrar Digital Persona USB HID en fingerprint_service"
```

---

#### E2-A04: Implementar captura facial con liveness detection
**Qué hacer:** Implementar `face_service.py` con MediaPipe FaceMesh y detección de parpadeo EAR.
**Dependencias:**
```
pip install mediapipe==0.10.14
```
**Lógica de liveness:**
```python
# EAR = Eye Aspect Ratio
# Landmarks ojo izquierdo: [362, 385, 387, 263, 373, 380]
# Landmarks ojo derecho:   [33,  160, 158, 133, 153, 144]
def calcular_ear(landmarks, indices_ojo):
    # p1-p6: puntos del ojo
    alto1 = distancia(p2, p6)
    alto2 = distancia(p3, p5)
    ancho = distancia(p1, p4)
    return (alto1 + alto2) / (2.0 * ancho)
# Si EAR < 0.25: ojo cerrado → cuenta parpadeo al reabrir
# Tras 3 parpadeos: captura frame, guarda JPEG
```
**Ruta de almacenamiento:** `static/fotos/{identificacion}_{timestamp}.jpg`
**Criterio de aceptación:** 3 parpadeos → foto guardada. Foto con face fake (print) no pasa.

---

#### E2-A05: Integrar YHD-9300 (condicional)
**Condición:** Solo si se confirma la compra del escáner.
**Qué hacer:** Implementar `scanner_service.py` con pyserial.
**`scanner_status()`** debe retornar `{"disponible": false}` si el escáner no está conectado sin lanzar excepción.
```python
def verificar_conexion(self) -> bool:
    try:
        self.conexion = serial.Serial(self.puerto, timeout=1)
        return True
    except serial.SerialException:
        return False  # no lanzar excepción — el sistema sigue funcionando
```

---

### ETAPA 3 — Servicios de negocio y controladores Java
**Fechas:** 26 abril – 9 mayo
**Responsable:** Daniel Turizo
**Rama:** `feature/services`
**Depende de:** E1 mergeada a develop
**Objetivo:** Toda la lógica electoral en Java. Flujo completo funcional.

---

#### E3-A01: Implementar AppConfig y AppServer
**Qué hacer:** Configurar HikariCP leyendo variables de entorno. Registrar todos los controladores en Javalin.
```java
// AppConfig.java
String dbUrl = System.getenv("ABIS_DB_URL");
String dbPass = System.getenv("ABIS_DB_PASSWORD");
if (dbUrl == null || dbPass == null) {
    throw new IllegalStateException("Variables ABIS_DB_URL y ABIS_DB_PASSWORD requeridas");
}
```

---

#### E3-A02: Implementar AdminService + AdminController
**Qué hacer:** Login con hash SHA-256. Token simple (UUID) almacenado en SESIONES.
**Flujo login:**
```
POST /api/admin/login
  → AdminRepository.findByUsuario()
  → HashingService.verificarPassword()
  → Si válido: SesionRepository.save(new Sesion(UUID, ahora))
  → Retorna: token
```
**Flujo logout:**
```
POST /api/admin/logout
  → SesionRepository.invalidarToken() → UPDATE SET FECHA_FIN = SYSTIMESTAMP
```

---

#### E3-A03: Implementar PreRegistroService + PreRegistroController
**Qué hacer:** Orquestar el flujo completo de registro de un votante.
**Flujo:**
```
1. POST /api/preregistro/ocr o /scanner → OcrResultado
2. POST /api/preregistro/registrar      → INSERT en VOTANTES (estado='PENDIENTE')
3. POST /api/preregistro/face           → FaceCaptureResult → UPDATE FOTO_URL
4. POST /api/preregistro/huella         → template raw → cifrar → UPDATE PLANTILLA_BIOMETRICA
```
**Cifrado del template:**
```java
byte[] templateRaw = ...; // viene de Python
String templateCifrado = cryptoService.cifrarTexto(Base64.encode(templateRaw));
String hash = hashingService.hashTemplate(templateRaw);
votanteRepo.actualizarPlantilla(identificacion, templateCifrado);
// guardar hash también
```

---

#### E3-A04: Implementar EleccionService + VotacionController
**Qué hacer:** Flujo del día de votación con `registrarVotoAtomico()`.
**Lógica atómica:**
```java
public boolean registrarVotoAtomico(String identificacion, Long idCandidato,
                                     Long idEleccion, Long idPuesto) {
    // 1. Verificar que no haya votado
    if (registroRepo.yaVoto(identificacion, idEleccion)) throw new YaVotoException();
    // 2. Verificar elección activa
    if (!eleccionRepo.findById(idEleccion).estaActiva()) throw new EleccionCerradaException();
    // 3. Obtener pesoVoto AHORA y congelarlo
    double pesoVotoAplicado = rolRepo.getPesoVoto(votante.getIdRol());
    // 4. INSERT en VOTOS (anónimo)
    Voto voto = new Voto(idCandidato, idEleccion, votante.getIdRol(), pesoVotoAplicado);
    votoRepo.save(voto);
    // 5. INSERT en REGISTRO_VOTOS (quién votó)
    RegistroVoto registro = new RegistroVoto(identificacion, idPuesto, idEleccion);
    registroRepo.save(registro);
    // 6. UPDATE estadoVoto = 'EJERCIDO'
    votanteRepo.actualizarEstado(identificacion, "EJERCIDO");
    // 7. Disparar notificación en hilo separado
    executor.submit(() -> notificacionService.enviarCertificadoVotacion(identificacion, idEleccion));
    return true;
}
```

---

#### E3-A05: Implementar RolService + JuradoService + controladores
**Qué hacer:** CRUD de roles con pesoVoto. Asignación de jurados a mesas.
**RolService:** Al editar pesoVoto, advertir que votos ya emitidos usan PESOVOTO_APLICADO (no se ven afectados).
**JuradoService:** `esJurado()` hace SELECT en JURADOS por identificacion.

---

#### E3-A06: Implementar NotificacionService + CertificadoPdfGenerator
**Qué hacer:** Generar PDF del certificado y enviarlo por SendGrid de forma asíncrona.
**Dependencia Maven:**
```xml
<dependency>
    <groupId>com.sendgrid</groupId>
    <artifactId>sendgrid-java</artifactId>
    <version>4.10.2</version>
</dependency>
```
**Siempre en hilo separado:** Si falla el correo, el voto ya quedó registrado. Admin puede reenviar.

---

### ETAPA 4 — Frontend
**Fechas:** 10–16 mayo
**Responsable:** Ana Laura Cuéllar (apoyo Daniel Turizo)
**Rama:** `feature/frontend`
**Objetivo:** 3 páginas funcionales conectadas al backend real.

---

#### E4-A01: Página de pre-registro
**Archivo:** `pages/auth/index.html` + JS
**Componentes:**
- Video en vivo de la cámara con bounding box dinámico
- Botón "Escanear documento" → `POST /api/preregistro/ocr`
- Formulario con datos del votante (pre-llenado desde OCR, editable)
- Botón "Capturar rostro" → `POST /api/preregistro/face` (cuenta parpadeos en pantalla)
- Botón "Registrar huella" → `POST /api/preregistro/huella`
- Botón "Guardar votante" → `POST /api/preregistro/registrar`

---

#### E4-A02: Página del kiosco de votación
**Archivo:** `pages/tarjeton/index.html` + JS
**Flujo:**
1. Pantalla espera → operador activa lector Digital Persona
2. `POST /api/biometric/verify` → retorna VotantePerfilDTO
3. Mostrar foto + nombre + rol + puesto del votante
4. Tarjetón agrupado por `cargo` de los candidatos
5. Votante selecciona → `POST /api/voto/registrar`
6. Pantalla de confirmación: "Voto registrado. Se enviará certificado a su correo"

---

#### E4-A03: Panel de administración
**Archivo:** `pages/admin/index.html` + JS
**Secciones:**
- Login → `POST /api/admin/login`
- Resultados electorales (tabla + gráfica simple) → `GET /api/admin/resultados/:id`
- Votantes registrados → `GET /api/admin/votantes`
- Gestión de roles (crear/editar con pesoVoto) → CRUD `/api/admin/roles`
- Gestión de mesas y jurados → `/api/admin/mesas` y `/api/admin/jurados`
- Reenvío de certificados → `POST /api/admin/certificado/reenviar`

---

### ETAPA 5 — Integración, pruebas y entrega
**Fechas:** 17–20 mayo
**Responsable:** Todo el equipo
**Objetivo:** Flujo completo funcional de extremo a extremo.

---

#### E5-A01: Prueba del flujo completo
**Escenario 1 — Pre-registro normal:**
```
Operador → escanea cédula → datos aparecen en formulario →
captura facial (3 parpadeos) → foto guardada →
registra huella Digital Persona → votante guardado en Oracle →
estado: PENDIENTE
```

**Escenario 2 — Día de votación:**
```
Votante → pone dedo en Digital Persona →
sistema identifica → muestra perfil →
votante vota → voto registrado →
certificado enviado al correo →
estado: EJERCIDO
```

**Escenario 3 — Intento de doble voto:**
```
Votante que ya votó → pone dedo →
sistema identifica → error: "Ya ejerció su voto" →
kiosco vuelve a pantalla inicial
```

---

#### E5-A02: Revisión de código limpio
**Checklist antes del merge final:**
- [ ] Sin `System.out.println()` en Java
- [ ] Sin `print()` en Python
- [ ] Sin credenciales hardcodeadas
- [ ] PreparedStatement en todas las consultas SQL
- [ ] Ninguna función supera 20 líneas
- [ ] Sin lógica de negocio en controladores
- [ ] Nombres descriptivos en todos los métodos

---

#### E5-A03: Merge final develop → main
**Responsable:** Daniel Turizo
**Procedimiento:**
```
git checkout develop
git pull origin develop
# Verificar que todo compila y corre
# Ejecutar el flujo completo manualmente
git checkout main
git merge --no-ff develop
git tag v1.0-mvp
git push origin main --tags
```

---

## 5. FLUJO DE TRABAJO GIT

### Estructura de ramas
```
main
  └── develop (solo Daniel Turizo hace merge aquí)
        ├── feature/dao-oracle    (Mateo + Jorge)
        ├── feature/security      (Daniel Flórez)
        ├── feature/frontend      (Ana Laura)
        ├── feature/biometric     (Daniel Turizo)
        └── feature/services      (Daniel Turizo)
```

### Flujo obligatorio por tarea

```bash
# ANTES de empezar cualquier tarea
git checkout develop
git pull origin develop
git checkout feature/mi-rama
git merge develop        # traer cambios recientes

# DURANTE el desarrollo
git add archivo.java     # nunca git add . (agrega archivos no deseados)
git commit -m "feat: implementar VotanteRepository.findByIdentificacion"

# AL TERMINAR
git push origin feature/mi-rama
# Abrir Pull Request en GitHub → hacia develop
# Esperar aprobación de Daniel Turizo
```

### Convención de commits
```
feat:     nueva funcionalidad
fix:      corrección de bug
refactor: mejora interna sin cambio de comportamiento
docs:     documentación
test:     pruebas
chore:    configuración, dependencias

Ejemplos reales:
feat: implementar JuradoRepository con PK compuesta
feat: integrar Digital Persona en fingerprint_service
fix: corregir UNIQUE constraint en REGISTRO_VOTOS
refactor: separar EleccionService en métodos de 15 líneas
test: agregar test unitario para HashingService.verificarIntegridad
```

### Reglas irrompibles
| Regla | Descripción |
|---|---|
| No push directo a main | Nunca. Sin excepción. |
| No push directo a develop | Solo Daniel Turizo hace merge aquí |
| PR obligatorio | Todo cambio requiere Pull Request revisado |
| Commits pequeños | Un commit = un cambio específico. No mezclar 10 cambios |
| Sin git add . | Usar `git add archivo` para evitar subir .env, .jks, etc. |

---

## 6. INTEGRACIÓN DE HARDWARE

### Digital Persona (Huella)
**Lado Python:** `FingerprintService` usa SDK dpfj (USB HID, sin puerto serial).
**Lado Java:** `BiometricClient.enrollFingerprint()` llama `POST /fingerprint/enroll`.
**Flujo de seguridad:**
```
Python captura → template raw ISO/IEC 19794-2
→ retorna a Java como bytes
→ Java: HashingService.hashTemplate(templateRaw) → hash integridad
→ Java: CryptoService.cifrarTexto(Base64(template)) → template cifrado
→ Java: INSERT en VOTANTES (PLANTILLA_BIOMETRICA, HASHINTEGRIDADBIOMETRICA)
```
**En verificación (día de votación):**
```
Java: VotanteRepository.findByIdentificacion() → template cifrado
→ Java: CryptoService.descifrarTexto() → template raw
→ Java: HashingService.verificarIntegridad() → ok/tampering
→ Java: BiometricClient.verifyFingerprint(template) → Python compara vs sensor
```

### Cámara + MediaPipe (Captura facial)
**Flujo pre-registro:**
```
Frontend: activa video en tiempo real →
POST /ocr/live-frame → BoundingBoxResponse → dibuja recuadro →
Operador alinea documento → POST /ocr/scan → datos extraídos
Luego: POST /face/capture → MediaPipe cuenta 3 parpadeos →
foto guardada en static/fotos/ → retorna foto_url →
Java guarda foto_url en VOTANTES.FOTO_URL
```

### YHD-9300 (Escáner 2D — condicional)
**Si disponible:** `POST /scanner/read` → ScannerService lee PDF417 → datos perfectos del documento.
**Si no disponible:** `scanner_status()` retorna `{"disponible": false}`, el frontend oculta el botón de escáner y solo muestra OCR.
**Impacto en BD:** Ninguno. Solo cambia el campo `fuente` en el OcrResponse ("pdf417" vs "tesseract").

---

## 7. PRINCIPIOS SOLID APLICADOS AL PROYECTO

### S — Single Responsibility Principle
Cada clase hace una sola cosa:
- `VotanteRepository` solo accede a VOTANTES. No conoce la lógica electoral.
- `EleccionService` orquesta el voto pero no sabe nada de SQL ni de hardware.
- `FingerprintService.py` solo habla con Digital Persona. No procesa documentos.

### O — Open/Closed Principle
Preparado para extensión sin modificación:
- Si se añade reconocimiento facial como método de identificación, se crea `FaceMatchService` implementando la misma interfaz que `BiometricService`. Sin tocar `VotacionController`.
- Si se migra a PostgreSQL, solo cambian los repositorios. Los servicios no se tocan.

### L — Liskov Substitution Principle
`VotanteRepository` puede reemplazarse por un mock en tests y `EleccionService` no se entera.

### I — Interface Segregation Principle
`Repository<T>` tiene solo los 5 métodos CRUD que todos necesitan. Métodos específicos (como `yaVoto`) van en `RegistroVotoRepository` directamente, no en la interfaz genérica.

### D — Dependency Inversion Principle
`EleccionService` depende de `Repository<T>` (abstracción), no de `VotanteRepository` directamente. En tests se inyecta un mock.

---

## 8. PRINCIPIOS GRASP APLICADOS

### Creator
`PreRegistroService` crea objetos `Votante` porque tiene todos los datos necesarios para ello.

### Information Expert
`RegistroVotoRepository.yaVoto()` verifica si un votante ya votó porque tiene acceso directo a la tabla REGISTRO_VOTOS — la que tiene la información.

### Low Coupling
`BiometricClient` es el único punto de acoplamiento entre Java y Python. Si Python cambia completamente, solo se modifica `BiometricClient`.

### High Cohesion
`JuradoService` agrupa toda la lógica relacionada con jurados y mesas. No hay lógica de jurados en `EleccionService` ni en `VotanteRepository`.

### Controller
`VotacionController` recibe el request HTTP, extrae parámetros, delega a `EleccionService`, retorna `ApiResponse`. No contiene lógica de negocio.

---

## 9. GESTIÓN DE RIESGOS

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Digital Persona no tiene SDK compatible con Python 3.11 | Alto | Tener `libfprint` como alternativa. Testear en Sprint 2. |
| YHD-9300 no llega antes de la entrega | Medio | El sistema funciona sin él. OCR como único canal. |
| MediaPipe no detecta parpadeos en sala con mala iluminación | Medio | Umbral EAR configurable en `config.py`. Ajustar en pruebas. |
| feature/dao-oracle se retrasa | Alto | feature/services puede arrancar con repositorios mockeados. |
| Oracle XE se corrompe en máquina de demostración | Alto | Script `datos_maestros.sql` permite recrear datos rápidamente. |

---

## APÉNDICE — Documentos de referencia

| Documento | Ubicación | Para quién |
|---|---|---|
| UML Backend Java v6 | `docs/uml-clases-backend-java.md` | Mateo, Jorge, Flórez, Daniel T. |
| UML Seguridad JCA | `docs/uml-security.md` | Daniel Flórez |
| UML Python v2 | `docs/uml-biometric-python.md` | Daniel Turizo |
| DDL tablas | `abis-database/ddl/01_create_tables.sql` | Todo el equipo |
| DDL foreign keys | `abis-database/ddl/02_foreign_keys.sql` | Todo el equipo |
| DML datos maestros | `abis-database/dml/datos_maestros.sql` | Todo el equipo |
| Variables de entorno | `.env.example` | Todo el equipo |
