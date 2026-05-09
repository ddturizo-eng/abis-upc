# UML — ABIS-UPC | Backend Java (abis-backend)
## v6 — Actualización hardware + BD final + captura facial
## Pega el bloque mermaid en: https://mermaid.live  o  https://www.mermaidchart.com
##
## ══════════════════════════════════════════════════════════════════
## PARA EL EQUIPO — LEE ESTO ANTES DE TOCAR CÓDIGO
## ══════════════════════════════════════════════════════════════════
##
## Este archivo modela ÚNICAMENTE el módulo abis-backend (Java 21 + Javalin 6).
## Python (abis-biometric) tiene su propio diagrama. El único puente entre
## Java y Python es BiometricClient → HTTP/JSON → FastAPI :8000.
##
## DISTRIBUCIÓN DE RAMAS Y RESPONSABLES:
##   feature/dao-oracle   → Ing. Mateo Calderón + Ing. Jorge Herrera
##   feature/security     → Ing. Daniel Flórez  (ver también uml-security.md)
##   feature/frontend     → Ing. Ana Laura Cuéllar
##   feature/biometric    → Ing. Daniel Turizo  (Python + integración Java)
##   feature/services     → Ing. Daniel Turizo  (servicios + controladores)
##   develop              → Ing. Daniel Turizo  (solo él hace merge aquí)
##
## ══════════════════════════════════════════════════════════════════
## MAPA JAVA ↔ ORACLE (nombres reales en BD)
## ══════════════════════════════════════════════════════════════════
## Los repositorios usan estos nombres EXACTOS en SQL:
##
## Tabla VOTANTES:
##   identificacion        → IDENTIFICACION
##   plantillaBiometrica   → PLANTILLA_BIOMETRICA
##   correo                → CORREO
##   primerNombre          → PRIMER_NOMBRE
##   segundoNombre         → SEGUNDO_NOMBRE
##   primerApellido        → PRIMER_APELLIDO
##   segundoApellido       → SEGUNDO_APELLIDO
##   estadoVoto            → ESTADO_VOTO  ('PENDIENTE'|'EJERCIDO'|'INHABILITADO')
##   fotoUrl               → FOTO_URL
##   fechaConsentimiento   → FECHA_CONSENTIMIENTO
##   hashIntegridadBiom.   → HASHINTEGRIDADBIOMETRICA
##   idRol                 → ROLES_IDROL
##   idPuesto              → PUESTOS_VOTACION_IDPUESTOS
##
## Tabla ROLES:
##   idRol    → ID_ROL
##   nombre   → NOMBRE
##   pesoVoto → PESO_VOTO
##
## Tabla PUESTOS_VOTACION:
##   idPuesto     → ID_PUESTOS
##   ciudad       → CIUDAD
##   sede         → SEDE
##   nombrePuesto → NOMBRE_PUESTO
##
## Tabla MESA_JURADOS:
##   idMesa    → ID_MESA
##   horaIngreso → HORA_INGRESO
##   horaSalida  → HORA_SALIDA
##   cargo       → CARGO
##   idPuesto    → PUESTOS_VOTACION_IDPUESTOS
##
## Tabla JURADOS (PK compuesta):
##   idMesa          → MESA_JURADOS_IDMESA
##   identificacion  → VOTANTES_IDENTIFICACION
##   fechaAsignacion → FECHA_ASIGNACION
##   cargo           → CARGO
##
## Tabla ELECCIONES:
##   idEleccion      → ID_ELECCION
##   nombre          → NOMBRE
##   fechaHoraInicio → FECHAHORA_INICIO
##   fechaHoraFin    → FECHAHORA_FIN
##   estado          → ESTADO  ('PROGRAMADA'|'EN_CURSO'|'CERRADA')
##
## Tabla CANDIDATOS:
##   idCandidato   → ID_CANDIDATO
##   primerNombre  → PRIMER_NOMBRE
##   segundoNombre → SEGUNDO_NOMBRE
##   primerApellido  → PRIMER_APELLIDO
##   segundoApellido → SEGUNDO_APELLIDO
##   numeroCampana → NUMERO_CAMPANIA
##   idEleccion    → ELECCIONES_IDELECCION
##   cargo         → CARGO
##
## Tabla VOTOS:
##   idVoto          → ID_VOTOS
##   idRol           → ROLES_IDROL
##   idEleccion      → ELECCIONES_IDELECCION
##   idCandidato     → IDCANDIDATO
##   fechaHora       → FECHA_HORA
##   pesoVotoAplicado → PESOVOTO_APLICADO
##
## Tabla REGISTRO_VOTOS:
##   idRegistro      → ID_REGISTRO
##   fechaHora       → FECHA_HORA
##   identificacion  → VOTANTES_IDENTIFICACION
##   idPuesto        → PUESTOS_VOTACION_IDPUESTOS
##   idEleccion      → ELECCIONES_IDELECCION
##
## Tabla ADMINISTRADORES:
##   idAdmin      → ID_ADMIN
##   usuario      → USUARIO
##   passwordHash → PASSWORD_HASH
##   nombre       → NOMBRE
##   correo       → CORREO
##
## Tabla SESIONES:
##   idSesion  → ID_SESION
##   token     → TOKEN
##   fechaInicio → FECHA_INICIO
##   idAdmin   → ADMINISTRADORES_IDADMIN
##   fechaFin  → FECHA_FIN  (NULL = sesión activa)
##
## ══════════════════════════════════════════════════════════════════
## DECISIONES DE DISEÑO — NO CAMBIAR SIN CONSENSO DEL EQUIPO
## ══════════════════════════════════════════════════════════════════
##
## [D1]  TIPO DOCUMENTO — No persiste en Oracle.
##       PK de VOTANTES es la identificacion (número).
##       El tipo (CC/TI/CE) solo viaja en VotanteDTO y se descarta.
##
## [D2]  FLUJO BIOMÉTRICO EN DOS FASES:
##       FASE 1 — Pre-registro (días previos):
##         Admin → OCR extrae datos del documento (+ escáner 2D si disponible) →
##         Cámara guía → OpenCV bounding box → captura foto rostro →
##         MediaPipe liveness (3 parpadeos) → foto validada → guarda fotoUrl →
##         Digital Persona captura huella → template → cifrado AES-256 → Oracle →
##         estadoVoto = 'PENDIENTE'
##       FASE 2 — Día de votación (kiosco):
##         Digital Persona captura huella → BiometricService descifra template →
##         Python compara → si coincide → kiosco muestra VotantePerfilDTO →
##         votante selecciona candidato → registrarVotoAtomico() →
##         estadoVoto = 'EJERCIDO' → [hilo separado] PDF + SendGrid
##
## [D3]  SECRETO DEL SUFRAGIO — Dos tablas separadas:
##       REGISTRO_VOTOS: sabe QUIÉN votó → fuente para certificados
##       VOTOS:          sabe A QUIÉN se votó → sin identificacion
##       REGLA: NUNCA hacer JOIN entre ambas en código Java.
##
## [D4]  CONTEO DE VOTOS — Sin totalVotos en CANDIDATOS.
##       Resultado = COUNT(*) sobre VOTOS agrupado por IDCANDIDATO.
##       Resultado ponderado = SUM(PESOVOTO_APLICADO) agrupado por IDCANDIDATO.
##
## [D5]  ADMINISTRADOR — Un solo nivel, acceso total.
##
## [D6]  JURADO — ES SU PROPIA TABLA (JURADOS), no un atributo de VOTANTES.
##       PK compuesta: (MESA_JURADOS_IDMESA, VOTANTES_IDENTIFICACION).
##       Un votante puede ser jurado en múltiples mesas (distintas elecciones).
##       Java determina si es jurado consultando JuradoRepository.
##
## [D7]  CARGOS — Eliminada como tabla. El campo CARGO vive en CANDIDATOS.
##       Se agrupa por candidatos.cargo para construir el tarjetón.
##
## [D8]  PUESTOS — Jerarquía: ciudad → sede → nombrePuesto.
##       Cada votante tiene PUESTOS_VOTACION_IDPUESTOS fijo.
##
## [D9]  ROLES — Datos en BD con pesoVoto. Admin los gestiona en runtime.
##       DML inicial: ESTUDIANTE=1.0, DOCENTE=2.0, ADMINISTRATIVO=1.5, EGRESADO=0.5
##
## [D10] NOMENCLATURA:
##       Clases Java → PascalCase singular (Votante, Eleccion)
##       Tablas Oracle → UPPER_SNAKE_CASE (VOTANTES, REGISTRO_VOTOS)
##       Columnas Oracle → UPPER_SNAKE_CASE (PRIMER_NOMBRE, FECHA_HORA)
##       FKs Oracle → TABLA_PADRE_IDCOLUMNA (ROLES_IDROL)
##
## [D11] CERTIFICADO PDF + SENDGRID — Flujo 100% asíncrono.
##       ExecutorService → NotificacionService → CertificadoPdfGenerator → byte[]
##
## [D12] HARDWARE BIOMÉTRICO ACTUALIZADO:
##       Huella: Digital Persona (USB HID, SDK dpfj/libfprint)
##         - Sin UART, sin circuito adaptador, plug and play
##         - Template ISO/IEC 19794-2, mayor tamaño → PLANTILLA_BIOMETRICA VARCHAR2(500)
##       Escáner 2D: YHD-9300 (estado: probable compra, no confirmada)
##         - Canal principal PDF417/QR si está disponible
##         - Si no disponible: OCR con OpenCV+Tesseract como único canal
##       Cara: MediaPipe liveness detection (3 parpadeos → captura)
##         - Solo para foto en pre-registro, no para identificación
##
## [D13] SEGURIDAD JCA + LEY 1581/2012:
##       KeyStoreManager → CryptoService AES-256 → cifra PLANTILLA_BIOMETRICA
##       HashingService SHA-256 → HASHINTEGRIDADBIOMETRICA
##       Ley 1581 Art.9: FECHA_CONSENTIMIENTO en VOTANTES
##       Ley 1581 Art.12: anonimizarDatosBiometricos() limpia huella y foto
##
## [D14] VARIABLES DE ENTORNO — Nunca hardcodear:
##       ABIS_KEYSTORE_PATH, ABIS_KEYSTORE_PASSWORD, ABIS_AES_KEY_ALIAS
##       ABIS_SENDGRID_API_KEY, ABIS_DB_URL, ABIS_DB_PASSWORD
##
## [D15] PESO DEL VOTO:
##       VOTOS.PESOVOTO_APLICADO congela el peso del rol en el momento del voto.
##       Garantiza integridad histórica si el admin cambia PESO_VOTO en ROLES.

```mermaid
classDiagram
  direction TB

  %% ╔══════════════════════════════════════════════════════════╗
  %% ║  CAPA DE DOMINIO — com.abisupc.model                     ║
  %% ║  Cada clase mapea 1-a-1 a una tabla Oracle               ║
  %% ╚══════════════════════════════════════════════════════════╝

  class Entity {
    <<abstract>>
    -id : Long
    +getId() Long
    +setId(id : Long) void
  }

  class Votante {
    %% Tabla: VOTANTES
    %% PK: IDENTIFICACION
    %% ESTADO_VOTO: 'PENDIENTE' | 'EJERCIDO' | 'INHABILITADO'
    %% Jurado: se determina consultando JuradoRepository (no hay campo aquí) [D6]
    %% PLANTILLA_BIOMETRICA: cifrada AES-256. Digital Persona → VARCHAR2(500) [D12]
    -identificacion : String
    -plantillaBiometrica : String
    -hashIntegridadBiometrica : String
    -correo : String
    -primerNombre : String
    -segundoNombre : String
    -primerApellido : String
    -segundoApellido : String
    -estadoVoto : String
    -fotoUrl : String
    -idRol : Long
    -idPuesto : Long
    -fechaConsentimiento : Timestamp
    +estaHabilitado() boolean
    +tieneHuellaRegistrada() boolean
    +marcarComoVotado() void
    +getIdentificacion() String
    +getNombreCompleto() String
    +getEstadoVoto() String
    +setEstadoVoto(estado : String) void
    +getIdRol() Long
    +getIdPuesto() Long
    +getFotoUrl() String
    +getCorreo() String
    +getFechaConsentimiento() Timestamp
  }

  class Rol {
    %% Tabla: ROLES — gestionado por Admin en runtime [D9]
    %% PESO_VOTO: determina el peso del voto según el rol
    -nombre : String
    -pesoVoto : double
    +getNombre() String
    +getPesoVoto() double
    +setNombre(nombre : String) void
    +setPesoVoto(peso : double) void
  }

  class PuestoVotacion {
    %% Tabla: PUESTOS_VOTACION — ciudad → sede → nombrePuesto [D8]
    -ciudad : String
    -sede : String
    -nombrePuesto : String
    +getCiudad() String
    +getSede() String
    +getNombrePuesto() String
    +getDescripcionCompleta() String
  }

  class MesaJurado {
    %% Tabla: MESA_JURADOS
    %% Una mesa pertenece a un puesto de votación
    %% Un jurado trabaja en una mesa
    -horaIngreso : Timestamp
    -horaSalida : Timestamp
    -cargo : String
    -idPuesto : Long
    +getHoraIngreso() Timestamp
    +getHoraSalida() Timestamp
    +getCargo() String
    +getIdPuesto() Long
    +isActiva() boolean
  }

  class Jurado {
    %% Tabla: JURADOS — PK compuesta (idMesa + identificacion) [D6]
    %% Un votante puede ser jurado en distintas mesas/elecciones
    %% No tiene ID propio — la PK es la combinación
    -idMesa : Long
    -identificacion : String
    -fechaAsignacion : Date
    -cargo : String
    +getIdMesa() Long
    +getIdentificacion() String
    +getFechaAsignacion() Date
    +getCargo() String
  }

  class Administrador {
    %% Tabla: ADMINISTRADORES — un solo nivel [D5]
    -usuario : String
    -passwordHash : String
    -nombre : String
    -correo : String
    +getUsuario() String
    +getNombre() String
    +getCorreo() String
    +verificarPassword(rawPassword : String) boolean
  }

  class Sesion {
    %% Tabla: SESIONES — FECHA_FIN NULL = activa
    -idAdmin : Long
    -token : String
    -fechaInicio : Timestamp
    -fechaFin : Timestamp
    +isActiva() boolean
    +getToken() String
    +getIdAdmin() Long
    +invalidar() void
  }

  class Eleccion {
    %% Tabla: ELECCIONES
    %% ESTADO: 'PROGRAMADA' | 'EN_CURSO' | 'CERRADA'
    -nombre : String
    -fechaHoraInicio : Timestamp
    -fechaHoraFin : Timestamp
    -estado : String
    +estaActiva() boolean
    +abrir() void
    +cerrar() void
    +getNombre() String
    +getEstado() String
  }

  class Candidato {
    %% Tabla: CANDIDATOS — CARGOS eliminada, cargo es campo de texto [D7]
    %% Se agrupa por candidatos.cargo para construir el tarjetón
    %% idEleccion: FK directa hacia ELECCIONES
    -primerNombre : String
    -segundoNombre : String
    -primerApellido : String
    -segundoApellido : String
    -numeroCampana : int
    -cargo : String
    -idEleccion : Long
    +getNombreCompleto() String
    +getNumeroCampana() int
    +getCargo() String
    +getIdEleccion() Long
  }

  class RegistroVoto {
    %% Tabla: REGISTRO_VOTOS — sabe QUIÉN votó [D3]
    %% UNIQUE(VOTANTES_IDENTIFICACION, ELECCIONES_IDELECCION) = anti-duplicidad
    %% NUNCA hacer JOIN con VOTOS en código Java
    -identificacion : String
    -idPuesto : Long
    -idEleccion : Long
    -fechaHora : Timestamp
    +getIdentificacion() String
    +getIdEleccion() Long
    +getIdPuesto() Long
    +getFechaHora() Timestamp
  }

  class Voto {
    %% Tabla: VOTOS — sabe A QUIÉN se votó [D3]
    %% SIN identificacion → secreto del sufragio garantizado
    %% pesoVotoAplicado: congelado al momento del voto [D15]
    -idCandidato : Long
    -idEleccion : Long
    -idRol : Long
    -pesoVotoAplicado : double
    -fechaHora : Timestamp
    +getIdCandidato() Long
    +getIdEleccion() Long
    +getIdRol() Long
    +getPesoVotoAplicado() double
    +getFechaHora() Timestamp
  }

  %% ╔══════════════════════════════════════════════════════════╗
  %% ║  ENUMERACIONES — com.abisupc.model                       ║
  %% ╚══════════════════════════════════════════════════════════╝

  class EstadoEleccion {
    <<enumeration>>
    PROGRAMADA
    EN_CURSO
    CERRADA
  }

  class EstadoVotante {
    <<enumeration>>
    PENDIENTE
    EJERCIDO
    INHABILITADO
  }

  %% ╔══════════════════════════════════════════════════════════╗
  %% ║  CAPA DE REPOSITORIOS — com.abisupc.repository           ║
  %% ║  SQL usa UPPER_SNAKE_CASE exacto de Oracle [D10]         ║
  %% ║  RAMA: feature/dao-oracle                                ║
  %% ╚══════════════════════════════════════════════════════════╝

  class Repository~T~ {
    <<interface>>
    +findById(id : Long) T
    +findAll() List~T~
    +save(entity : T) void
    +update(entity : T) void
    +delete(id : Long) void
  }

  class VotanteRepository {
    <<Repository Pattern>>
    -dataSource : HikariDataSource
    +findById(id : Long) Votante
    +findAll() List~Votante~
    +save(v : Votante) void
    +update(v : Votante) void
    +delete(id : Long) void
    +findByIdentificacion(identificacion : String) Votante
    +findByIdPuesto(idPuesto : Long) List~Votante~
    +findByIdRol(idRol : Long) List~Votante~
    +tieneHuellaRegistrada(identificacion : String) boolean
    +estaHabilitado(identificacion : String) boolean
    +actualizarEstado(identificacion : String, estado : String) void
    +actualizarPlantilla(identificacion : String, templateCifrado : String) void
    +actualizarFoto(identificacion : String, fotoUrl : String) void
    +anonimizarDatosBiometricos(identificacion : String) void
  }

  class RolRepository {
    <<Repository Pattern>>
    -dataSource : HikariDataSource
    +findById(id : Long) Rol
    +findAll() List~Rol~
    +save(r : Rol) void
    +update(r : Rol) void
    +delete(id : Long) void
    +findByNombre(nombre : String) Rol
    +existeNombre(nombre : String) boolean
    +estaEnUso(idRol : Long) boolean
    +getPesoVoto(idRol : Long) double
  }

  class PuestoVotacionRepository {
    <<Repository Pattern>>
    -dataSource : HikariDataSource
    +findById(id : Long) PuestoVotacion
    +findAll() List~PuestoVotacion~
    +save(p : PuestoVotacion) void
    +update(p : PuestoVotacion) void
    +delete(id : Long) void
    +findByCiudad(ciudad : String) List~PuestoVotacion~
    +findBySede(sede : String) List~PuestoVotacion~
  }

  class MesaJuradoRepository {
    <<Repository Pattern>>
    -dataSource : HikariDataSource
    +findById(id : Long) MesaJurado
    +findAll() List~MesaJurado~
    +save(m : MesaJurado) void
    +update(m : MesaJurado) void
    +delete(id : Long) void
    +findByPuesto(idPuesto : Long) List~MesaJurado~
    +findActivas() List~MesaJurado~
  }

  class JuradoRepository {
    <<Repository Pattern — PK compuesta>>
    -dataSource : HikariDataSource
    +findById(id : Long) Jurado
    +findAll() List~Jurado~
    +save(j : Jurado) void
    +update(j : Jurado) void
    +delete(id : Long) void
    +findByIdentificacion(identificacion : String) List~Jurado~
    +findByMesa(idMesa : Long) List~Jurado~
    +esJurado(identificacion : String) boolean
    +asignarAMesa(identificacion : String, idMesa : Long, cargo : String) void
  }

  class AdministradorRepository {
    <<Repository Pattern>>
    -dataSource : HikariDataSource
    +findById(id : Long) Administrador
    +findAll() List~Administrador~
    +save(a : Administrador) void
    +update(a : Administrador) void
    +delete(id : Long) void
    +findByUsuario(usuario : String) Administrador
    +findByCorreo(correo : String) Administrador
  }

  class SesionRepository {
    <<Repository Pattern>>
    -dataSource : HikariDataSource
    +findById(id : Long) Sesion
    +findAll() List~Sesion~
    +save(s : Sesion) void
    +update(s : Sesion) void
    +delete(id : Long) void
    +findByToken(token : String) Sesion
    +findActivaByAdmin(idAdmin : Long) Sesion
    +invalidarToken(token : String) void
    +limpiarSesionesExpiradas() void
  }

  class EleccionRepository {
    <<Repository Pattern>>
    -dataSource : HikariDataSource
    +findById(id : Long) Eleccion
    +findAll() List~Eleccion~
    +save(e : Eleccion) void
    +update(e : Eleccion) void
    +delete(id : Long) void
    +findActiva() Eleccion
    +findByEstado(estado : String) List~Eleccion~
    +actualizarEstado(idEleccion : Long, estado : String) void
  }

  class CandidatoRepository {
    <<Repository Pattern>>
    %% Sin CargoRepository — cargo es campo de texto en Candidato [D7]
    -dataSource : HikariDataSource
    +findById(id : Long) Candidato
    +findAll() List~Candidato~
    +save(c : Candidato) void
    +update(c : Candidato) void
    +delete(id : Long) void
    +findByEleccion(idEleccion : Long) List~Candidato~
    +findByCargo(idEleccion : Long, cargo : String) List~Candidato~
    +getCargosDistintos(idEleccion : Long) List~String~
  }

  class RegistroVotoRepository {
    <<Repository Pattern>>
    -dataSource : HikariDataSource
    +findById(id : Long) RegistroVoto
    +findAll() List~RegistroVoto~
    +save(r : RegistroVoto) void
    +update(r : RegistroVoto) void
    +delete(id : Long) void
    +yaVoto(identificacion : String, idEleccion : Long) boolean
    +findByEleccion(idEleccion : Long) List~RegistroVoto~
    +findByIdentificacion(identificacion : String) List~RegistroVoto~
    +countByPuesto(idPuesto : Long, idEleccion : Long) int
  }

  class VotoRepository {
    <<Repository Pattern>>
    -dataSource : HikariDataSource
    +findById(id : Long) Voto
    +findAll() List~Voto~
    +save(v : Voto) void
    +update(v : Voto) void
    +delete(id : Long) void
    +findByEleccion(idEleccion : Long) List~Voto~
    +countByCandidato(idCandidato : Long) int
    +obtenerResultados(idEleccion : Long) Map~Long_Integer~
    +obtenerResultadosPonderados(idEleccion : Long) Map~Long_Double~
    +obtenerResultadosPorRol(idEleccion : Long) Map~String_Integer~
  }

  %% ╔══════════════════════════════════════════════════════════╗
  %% ║  CAPA DE SEGURIDAD — com.abisupc.security                ║
  %% ║  RAMA: feature/security — Ing. Daniel Flórez             ║
  %% ╚══════════════════════════════════════════════════════════╝

  class KeyStoreManager {
    <<Singleton — Security>>
    -keystorePath : String$
    -keystorePassword : String$
    -keyAlias : String$
    -keyStore : KeyStore
    +getInstance() KeyStoreManager$
    +getAesKey() SecretKey
    -cargarKeyStore() void
    -validarVariablesEntorno() void
  }

  class CryptoService {
    <<Service — Security>>
    -keyStoreManager : KeyStoreManager
    -ALGORITMO : String$
    +cifrar(datos : byte[]) byte[]
    +descifrar(datosCifrados : byte[]) byte[]
    +cifrarTexto(texto : String) String
    +descifrarTexto(textoCifrado : String) String
  }

  class HashingService {
    <<Service — Security>>
    -ALGORITMO_HASH : String$
    +hashTemplate(template : byte[]) String$
    +hashPassword(rawPassword : String) String$
    +verificarPassword(rawPassword : String, hash : String) boolean$
    +verificarIntegridad(template : byte[], hashAlmacenado : String) boolean$
  }

  class AuthMiddleware {
    <<Middleware — Security>>
    -sesionRepo : SesionRepository
    +authenticate(ctx : Context) void$
    +requireAdmin(ctx : Context) void$
    -validarToken(token : String) boolean
    -extraerToken(ctx : Context) String
  }

  %% ╔══════════════════════════════════════════════════════════╗
  %% ║  CAPA DE SERVICIOS — com.abisupc.business                ║
  %% ║  RAMA: feature/services — Ing. Daniel Turizo             ║
  %% ╚══════════════════════════════════════════════════════════╝

  class EleccionService {
    <<Service>>
    %% registrarVotoAtomico: INSERT en VOTOS y REGISTRO_VOTOS en la misma
    %% transacción Java. pesoVotoAplicado se lee de Rol y se congela [D15].
    -votanteRepo : VotanteRepository
    -registroRepo : RegistroVotoRepository
    -votoRepo : VotoRepository
    -eleccionRepo : EleccionRepository
    -rolRepo : RolRepository
    -notificacionService : NotificacionService
    -executor : ExecutorService
    +identificarPorHuella(sample : byte[]) Votante
    +registrarVotoAtomico(identificacion : String, idCandidato : Long, idEleccion : Long, idPuesto : Long) boolean
    +obtenerResultados(idEleccion : Long) Map~String_Object~
    +obtenerResultadosPonderados(idEleccion : Long) Map~String_Object~
    -verificarNoHaVotado(identificacion : String, idEleccion : Long) void
    -verificarEleccionActiva(idEleccion : Long) void
    -dispararEnvioAsincronico(identificacion : String, idEleccion : Long) void
  }

  class PreRegistroService {
    <<Service>>
    %% Flujo: OCR/Escáner → datos → captura facial liveness → foto →
    %% Digital Persona → huella → cifrar template → Oracle
    -votanteRepo : VotanteRepository
    -biometricClient : BiometricClient
    -cryptoService : CryptoService
    -hashingService : HashingService
    +preRegistrarVotante(dto : VotanteDTO) Votante
    +enrollarHuella(identificacion : String, templateRaw : byte[]) void
    +actualizarFoto(identificacion : String, fotoUrl : String) void
    +obtenerVotantePorIdentificacion(identificacion : String) Votante
    -cifrarYGuardarTemplate(identificacion : String, template : byte[]) void
    -validarVotanteNoExiste(identificacion : String) void
    -registrarConsentimiento(identificacion : String) void
  }

  class BiometricService {
    <<Service>>
    %% Integra tres canales biométricos:
    %% 1. OCR (OpenCV+Tesseract) → datos del documento
    %% 2. Digital Persona (USB HID) → template de huella
    %% 3. MediaPipe liveness → captura de foto de rostro
    %% El escáner YHD-9300 es canal adicional si disponible
    -votanteRepo : VotanteRepository
    -biometricClient : BiometricClient
    -cryptoService : CryptoService
    +verificarHuella(identificacion : String, sample : byte[]) FingerprintMatchResult
    +capturarYEnrolarHuella(identificacion : String) void
    +escanearDocumentoOCR(stream : InputStream, filename : String) OcrResultado
    +escanearDocumentoScanner() OcrResultado
    +capturarRostroConLiveness() FaceCaptureResult
    -descifrarTemplate(identificacion : String) byte[]
  }

  class AdminService {
    <<Service>>
    -adminRepo : AdministradorRepository
    -sesionRepo : SesionRepository
    -hashingService : HashingService
    +login(usuario : String, rawPassword : String) Sesion
    +logout(token : String) void
    +validarToken(token : String) boolean
    +getAdminByToken(token : String) Administrador
  }

  class RolService {
    <<Service>>
    -rolRepo : RolRepository
    -votanteRepo : VotanteRepository
    +crearRol(nombre : String, pesoVoto : double) Rol
    +editarRol(idRol : Long, nombre : String, pesoVoto : double) Rol
    +eliminarRol(idRol : Long) void
    +listarRoles() List~Rol~
    +asignarRolAVotante(identificacion : String, idRol : Long) void
    -validarNombreUnico(nombre : String) void
    -validarRolNoEnUso(idRol : Long) void
  }

  class JuradoService {
    <<Service>>
    %% Gestiona asignación de jurados a mesas [D6]
    -juradoRepo : JuradoRepository
    -mesaJuradoRepo : MesaJuradoRepository
    -votanteRepo : VotanteRepository
    +asignarJurado(identificacion : String, idMesa : Long, cargo : String) Jurado
    +listarJuradosPorMesa(idMesa : Long) List~Jurado~
    +esJurado(identificacion : String) boolean
    +listarMesas() List~MesaJurado~
    +crearMesa(idPuesto : Long, cargo : String) MesaJurado
  }

  class NotificacionService {
    <<Service>>
    -SENDGRID_API_KEY : String$
    -FROM_EMAIL : String$
    -pdfGenerator : CertificadoPdfGenerator
    -registroRepo : RegistroVotoRepository
    -votanteRepo : VotanteRepository
    -eleccionRepo : EleccionRepository
    +enviarCertificadoVotacion(identificacion : String, idEleccion : Long) void
    +reenviarCertificado(identificacion : String, idEleccion : Long) void
    -construirEmail(votante : Votante, eleccion : Eleccion, pdfBytes : byte[]) SendGridMail
    -enviarConSendGrid(mail : SendGridMail) void
  }

  class CertificadoPdfGenerator {
    <<Service>>
    +generarCertificado(dto : CertificadoDTO) byte[]
    -construirEncabezado(doc : Object, dto : CertificadoDTO) void
    -construirCuerpo(doc : Object, dto : CertificadoDTO) void
    -construirPieDePagina(doc : Object, dto : CertificadoDTO) void
    -generarCodigoVerificacion(identificacion : String, idEleccion : Long) String
  }

  %% ╔══════════════════════════════════════════════════════════╗
  %% ║  DTOs — com.abisupc.dto                                  ║
  %% ╚══════════════════════════════════════════════════════════╝

  class ApiResponse~T~ {
    -success : boolean
    -message : String
    -data : T
    +ok(data : T) ApiResponse~T~$
    +error(message : String) ApiResponse~T~$
    +isSuccess() boolean
    +getMessage() String
    +getData() T
  }

  class VotanteDTO {
    %% tipoDocumento NO persiste en Oracle [D1]
    +identificacion : String
    +tipoDocumento : String
    +primerNombre : String
    +segundoNombre : String
    +primerApellido : String
    +segundoApellido : String
    +correo : String
    +estadoVoto : String
    +idRol : Long
    +idPuesto : Long
    +fromOcrResult(ocr : OcrResultado) VotanteDTO$
    +toVotante() Votante
  }

  class VotantePerfilDTO {
    %% Respuesta al kiosco tras identificar por huella [D2 Fase 2]
    +identificacion : String
    +nombreCompleto : String
    +fotoUrl : String
    +nombreRol : String
    +nombrePuesto : String
    +estadoVoto : String
    +yaVoto() boolean
  }

  class RegistroVotoRequest {
    +identificacion : String
    +idCandidato : Long
    +idEleccion : Long
    +idPuesto : Long
    +tokenSesion : String
  }

  class ResultadoEleccionDTO {
    +idEleccion : Long
    +nombreEleccion : String
    +resultadosPorCargo : Map~String_List~
    +resultadosPonderados : Map~String_Double~
    +resultadosPorRol : Map~String_Integer~
    +totalVotos : int
  }

  class CertificadoDTO {
    +identificacion : String
    +nombreCompleto : String
    +nombreEleccion : String
    +fechaHoraVoto : Timestamp
    +nombrePuesto : String
    +sede : String
    +ciudad : String
    +correoDestinatario : String
    +codigoVerificacion : String
  }

  class OcrResultado {
    %% fuente: "tesseract" | "pdf417" | "qr" [D12]
    +tipoDoc : String
    +labelTipo : String
    +nombres : String
    +apellidos : String
    +nombreCompleto : String
    +numeroId : String
    +fechaNacimiento : String
    +sexo : String
    +fuente : String
    +confianza : int
  }

  class FingerprintMatchResult {
    +matched : boolean
    +score : int
    +identificacion : String
    +mensaje : String
    +fuente : String
  }

  class FaceCaptureResult {
    %% Resultado de la captura facial con liveness detection [D12]
    %% fotoBase64: imagen capturada tras 3 parpadeos
    %% landmarks: puntos faciales detectados (MediaPipe)
    +exitoso : boolean
    +fotoBase64 : String
    +fotoUrl : String
    +parpadeosDETECTADOS : int
    +mensaje : String
    +confianzaLiveness : int
  }

  %% ╔══════════════════════════════════════════════════════════╗
  %% ║  CAPA DE INTEGRACIÓN — com.abisupc.integration           ║
  %% ║  ÚNICO puente Java → Python :8000                        ║
  %% ║  RAMA: feature/biometric — Ing. Daniel Turizo            ║
  %% ╚══════════════════════════════════════════════════════════╝

  class BiometricClient {
    <<Integration Layer — Facade>>
    %% Java no sabe que existe Digital Persona, ni YHD-9300, ni MediaPipe.
    %% Java solo conoce esta interfaz.
    %% scanBarcode() disponible si YHD-9300 está conectado [D12]
    -BIOMETRIC_BASE_URL : String$
    +scanDocument(imageStream : InputStream, filename : String) OcrResultado$
    +scanBarcode() OcrResultado$
    +enrollFingerprint(identificacion : String) FingerprintEnrollData$
    +verifyFingerprint(identificacion : String, templateCifrado : String) FingerprintMatchResult$
    +capturarRostroLiveness() FaceCaptureResult$
    +isAlive() boolean$
    -post(endpoint : String, body : Object) HttpResponse~JsonNode~$
  }

  %% ╔══════════════════════════════════════════════════════════╗
  %% ║  CONTROLADORES — com.abisupc.controller                  ║
  %% ╚══════════════════════════════════════════════════════════╝

  class VotacionController {
    <<Controller>>
    %% GET  /api/health
    %% POST /api/biometric/verify → identifica por huella (Digital Persona)
    %% POST /api/voto/registrar   → voto atómico con peso del rol
    +register(app : Javalin) void$
    -handleHealth(ctx : Context) void
    -handleIdentificarPorHuella(ctx : Context) void
    -handleRegistrarVoto(ctx : Context) void
  }

  class PreRegistroController {
    <<Controller>>
    %% POST /api/preregistro/scanner  → YHD-9300 si disponible
    %% POST /api/preregistro/ocr      → cámara + Tesseract (siempre disponible)
    %% POST /api/preregistro/registrar→ guarda votante en BD
    %% POST /api/preregistro/huella   → Digital Persona enroll
    %% POST /api/preregistro/face     → captura facial + liveness detection
    %% GET  /api/preregistro/:id      → busca votante
    +register(app : Javalin) void$
    -handleScannerScan(ctx : Context) void
    -handleOcrScan(ctx : Context) void
    -handlePreRegistrar(ctx : Context) void
    -handleEnrolarHuella(ctx : Context) void
    -handleCapturarRostro(ctx : Context) void
    -handleGetVotante(ctx : Context) void
  }

  class AdminController {
    <<Controller>>
    %% POST /api/admin/login
    %% POST /api/admin/logout
    %% GET  /api/admin/resultados/:idEleccion → incluye resultados ponderados
    %% GET  /api/admin/votantes
    %% POST /api/admin/certificados/:idEleccion
    %% POST /api/admin/certificado/reenviar
    +register(app : Javalin) void$
    -handleLogin(ctx : Context) void
    -handleLogout(ctx : Context) void
    -handleGetResultados(ctx : Context) void
    -handleGetVotantes(ctx : Context) void
    -handleEnviarCertificados(ctx : Context) void
    -handleReenviarCertificado(ctx : Context) void
  }

  class RolController {
    <<Controller>>
    %% GET    /api/admin/roles
    %% POST   /api/admin/roles      → incluye pesoVoto
    %% PUT    /api/admin/roles/:id  → puede actualizar pesoVoto
    %% DELETE /api/admin/roles/:id
    %% PATCH  /api/admin/votantes/:id/rol
    +register(app : Javalin) void$
    -handleListar(ctx : Context) void
    -handleCrear(ctx : Context) void
    -handleEditar(ctx : Context) void
    -handleEliminar(ctx : Context) void
    -handleAsignarARol(ctx : Context) void
  }

  class JuradoController {
    <<Controller>>
    %% GET  /api/admin/mesas           → listar mesas de votación
    %% POST /api/admin/mesas           → crear mesa
    %% GET  /api/admin/jurados/:idMesa → jurados por mesa
    %% POST /api/admin/jurados         → asignar jurado a mesa
    +register(app : Javalin) void$
    -handleListarMesas(ctx : Context) void
    -handleCrearMesa(ctx : Context) void
    -handleListarJurados(ctx : Context) void
    -handleAsignarJurado(ctx : Context) void
  }

  %% ╔══════════════════════════════════════════════════════════╗
  %% ║  SERVIDOR Y CONFIGURACIÓN                                ║
  %% ╚══════════════════════════════════════════════════════════╝

  class AppServer {
    <<Singleton>>
    -app : Javalin
    +main(args : String[])$
    -buildApp() Javalin
    -registerRoutes() void
    -initSecurity() void
  }

  class AppConfig {
    <<Singleton>>
    -dataSource : HikariDataSource
    +configure(app : Object) void$
    +getDataSource() HikariDataSource$
    -initHikariPool() void
    -validarVariablesEntorno() void
  }

  %% ══════════════════════════════════════════════════════════
  %% RELACIONES — Herencia
  %% ══════════════════════════════════════════════════════════

  Entity <|-- Votante
  Entity <|-- Rol
  Entity <|-- PuestoVotacion
  Entity <|-- MesaJurado
  Entity <|-- Administrador
  Entity <|-- Sesion
  Entity <|-- Eleccion
  Entity <|-- Candidato
  Entity <|-- RegistroVoto
  Entity <|-- Voto

  Repository~T~ <|.. VotanteRepository : implements
  Repository~T~ <|.. RolRepository : implements
  Repository~T~ <|.. PuestoVotacionRepository : implements
  Repository~T~ <|.. MesaJuradoRepository : implements
  Repository~T~ <|.. JuradoRepository : implements
  Repository~T~ <|.. AdministradorRepository : implements
  Repository~T~ <|.. SesionRepository : implements
  Repository~T~ <|.. EleccionRepository : implements
  Repository~T~ <|.. CandidatoRepository : implements
  Repository~T~ <|.. RegistroVotoRepository : implements
  Repository~T~ <|.. VotoRepository : implements

  %% ══════════════════════════════════════════════════════════
  %% RELACIONES — Composición
  %% ══════════════════════════════════════════════════════════

  Administrador *-- Sesion : posee >
  KeyStoreManager *-- CryptoService : provee llave a >
  EleccionService *-- VotanteRepository : posee >
  EleccionService *-- RegistroVotoRepository : posee >
  EleccionService *-- VotoRepository : posee >
  EleccionService *-- EleccionRepository : posee >
  EleccionService *-- RolRepository : posee >
  EleccionService *-- NotificacionService : posee >
  PreRegistroService *-- VotanteRepository : posee >
  PreRegistroService *-- BiometricClient : posee >
  PreRegistroService *-- CryptoService : posee >
  PreRegistroService *-- HashingService : posee >
  BiometricService *-- VotanteRepository : posee >
  BiometricService *-- BiometricClient : posee >
  BiometricService *-- CryptoService : posee >
  JuradoService *-- JuradoRepository : posee >
  JuradoService *-- MesaJuradoRepository : posee >
  JuradoService *-- VotanteRepository : posee >
  RolService *-- RolRepository : posee >
  RolService *-- VotanteRepository : posee >
  AdminService *-- AdministradorRepository : posee >
  AdminService *-- SesionRepository : posee >
  AdminService *-- HashingService : posee >
  NotificacionService *-- CertificadoPdfGenerator : posee >
  NotificacionService *-- RegistroVotoRepository : posee >
  NotificacionService *-- VotanteRepository : posee >
  NotificacionService *-- EleccionRepository : posee >
  AppServer *-- VotacionController : inicializa >
  AppServer *-- PreRegistroController : inicializa >
  AppServer *-- AdminController : inicializa >
  AppServer *-- RolController : inicializa >
  AppServer *-- JuradoController : inicializa >
  AppServer *-- AuthMiddleware : aplica >
  AppServer *-- AppConfig : configura con >
  AppServer *-- KeyStoreManager : inicializa >

  %% ══════════════════════════════════════════════════════════
  %% RELACIONES — Agregación
  %% ══════════════════════════════════════════════════════════

  Votante o-- Rol : tiene asignado >
  Votante o-- PuestoVotacion : asignado a >
  MesaJurado o-- PuestoVotacion : pertenece a >
  Jurado o-- Votante : es un >
  Jurado o-- MesaJurado : trabaja en >
  RegistroVoto o-- Votante : referencia >
  RegistroVoto o-- PuestoVotacion : emitido en >
  RegistroVoto o-- Eleccion : UNIQUE(identificacion+idEleccion) >
  Voto o-- Candidato : voto para >
  Voto o-- Eleccion : en contexto de >
  Voto o-- Rol : peso aplicado de >
  Candidato o-- Eleccion : postula en >

  %% ══════════════════════════════════════════════════════════
  %% RELACIONES — Dependencia
  %% ══════════════════════════════════════════════════════════

  VotacionController ..> EleccionService : delega
  VotacionController ..> BiometricService : delega
  VotacionController ..> ApiResponse~T~ : retorna
  VotacionController ..> VotantePerfilDTO : retorna
  PreRegistroController ..> PreRegistroService : delega
  PreRegistroController ..> BiometricService : delega
  PreRegistroController ..> ApiResponse~T~ : retorna
  AdminController ..> AdminService : delega
  AdminController ..> NotificacionService : delega
  AdminController ..> ApiResponse~T~ : retorna
  AdminController ..> ResultadoEleccionDTO : retorna
  RolController ..> RolService : delega
  RolController ..> ApiResponse~T~ : retorna
  JuradoController ..> JuradoService : delega
  JuradoController ..> ApiResponse~T~ : retorna
  AuthMiddleware ..> SesionRepository : valida token
  EleccionService ..> RegistroVotoRequest : recibe
  EleccionService ..> EstadoVotante : aplica
  EleccionService ..> EstadoEleccion : verifica
  BiometricService ..> FingerprintMatchResult : produce
  BiometricService ..> OcrResultado : produce
  BiometricService ..> FaceCaptureResult : produce
  BiometricClient ..> OcrResultado : deserializa JSON
  BiometricClient ..> FaceCaptureResult : deserializa JSON
  VotanteDTO ..> OcrResultado : construido desde
  VotantePerfilDTO ..> Votante : construido desde
  AdminService ..> Sesion : gestiona
  NotificacionService ..> CertificadoDTO : construye
  CertificadoPdfGenerator ..> CertificadoDTO : renderiza
  CryptoService ..> KeyStoreManager : consulta llave
```
