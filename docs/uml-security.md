# UML — ABIS-UPC | Capa de Seguridad JCA + Ley 1581/2012
## Documento exclusivo para: feature/security — Ing. Daniel Flórez
## Pega el bloque mermaid en: https://mermaid.live  o  https://www.mermaidchart.com
##
## ══════════════════════════════════════════════════════════════════
## CONTEXTO PARA DANIEL FLÓREZ
## ══════════════════════════════════════════════════════════════════
##
## Este diagrama cubre EXCLUSIVAMENTE tu rama: feature/security
## Paquete Java: com.abisupc.security
##
## Tu trabajo no depende de que feature/dao-oracle esté listo porque
## CryptoService, HashingService y KeyStoreManager trabajan en memoria.
## Puedes arrancar desde el día uno.
##
## ══════════════════════════════════════════════════════════════════
## MARCO LEGAL — LEY 1581 DE 2012 (HABEAS DATA)
## ══════════════════════════════════════════════════════════════════
##
## Art. 9 — Autorización del titular:
##   El votante debe dar consentimiento EXPLÍCITO antes del pre-registro.
##   Se registra en VOTANTES.fechaConsentimiento (Timestamp).
##   PreRegistroService.registrarConsentimiento() lo persiste.
##
## Art. 12 — Derecho de supresión:
##   VotanteRepository.anonimizarDatosBiometricos() borra
##   plantillaBiometrica y fotoUrl pero conserva el registro electoral.
##   Esta operación NO elimina la fila — solo limpia los datos biométricos.
##
## Art. 17 — Deber de seguridad:
##   Justificación legal directa para CryptoService y KeyStoreManager.
##   Cualquier auditoría puede citar este artículo para explicar AES-256.
##
## ══════════════════════════════════════════════════════════════════
## VARIABLES DE ENTORNO REQUERIDAS — configurar ANTES de arrancar
## ══════════════════════════════════════════════════════════════════
##
## ABIS_KEYSTORE_PATH      → ruta al archivo .jks en el sistema
## ABIS_KEYSTORE_PASSWORD  → contraseña maestra del KeyStore
## ABIS_AES_KEY_ALIAS      → alias de la llave AES-256 dentro del .jks
##
## Para generar el archivo .jks (ejecutar UNA sola vez por máquina):
##   keytool -genseckey -alias abis-aes-key -keyalg AES -keysize 256
##            -storetype JCEKS -keystore abis-upc.jks
##
## El archivo .jks está en .gitignore — NUNCA va al repositorio.
## Cada desarrollador genera el suyo propio para desarrollo local.
## El de producción lo genera y custodia únicamente el administrador.
##
## ══════════════════════════════════════════════════════════════════
## QUÉ DATOS SE CIFRAN CON AES-256
## ══════════════════════════════════════════════════════════════════
##
## plantillaBiometrica: el template del AS608 antes de guardarse en Oracle.
##   PreRegistroService llama a CryptoService.cifrarTexto() antes del save().
##   BiometricService llama a CryptoService.descifrarTexto() antes de enviar
##   el template a Python para comparación.
##
## fotoUrl: la URL de la foto del votante también se cifra en reposo.
##   Se descifra solo cuando VotantePerfilDTO necesita mostrarla en kiosco.
##
## ══════════════════════════════════════════════════════════════════
## QUÉ SE HASHEA CON SHA-256
## ══════════════════════════════════════════════════════════════════
##
## hashIntegridadBiometrica: SHA-256 del template original (antes de cifrar).
##   Se guarda en VOTANTES.hashIntegridadBiometrica.
##   Al leer el template de vuelta, se recalcula el hash y se compara.
##   Si no coincide → el template fue alterado en la BD → alerta de seguridad.
##
## passwordHash en Administrador: BCrypt no está en JCA estándar.
##   Usar SHA-256 con salt o añadir la dependencia jBCrypt al pom.xml.
##   Recomendación: SHA-256 + salt aleatorio almacenado junto al hash.
##
## ══════════════════════════════════════════════════════════════════
## RELACIÓN CON OTRAS RAMAS
## ══════════════════════════════════════════════════════════════════
##
## feature/dao-oracle (Mateo + Jorge):
##   VotanteRepository.anonimizarDatosBiometricos() → ellos implementan el método
##   VOTANTES.fechaConsentimiento → ellos añaden la columna al DDL
##   VOTANTES.hashIntegridadBiometrica → ellos añaden la columna al DDL
##
## feature/services (Daniel Turizo):
##   PreRegistroService usa CryptoService y HashingService
##   BiometricService usa CryptoService
##   AdminService usa HashingService para verificar passwords
##
## Tu entregable: las 3 clases de este diagrama compilando y con tests unitarios.
## Daniel Turizo las inyecta en los servicios cuando merge feature/dao-oracle.

```mermaid
classDiagram
  direction TB

  %% ╔══════════════════════════════════════════════════════════╗
  %% ║  CAPA DE SEGURIDAD — com.abisupc.security                ║
  %% ║  Implementa JCA (Java Cryptography Architecture)         ║
  %% ║  Cumplimiento: Ley 1581/2012 Arts. 9, 12 y 17            ║
  %% ╚══════════════════════════════════════════════════════════╝

  class KeyStoreManager {
    <<Singleton>>
    %% Patrón Singleton: una sola instancia en todo el sistema
    %% Lee TRES variables de entorno al arrancar (ver encabezado)
    %% Si alguna variable falta → lanza IllegalStateException en startup
    %% NUNCA expone la SecretKey fuera de esta clase
    -instance : KeyStoreManager$
    -keystorePath : String
    -keystorePassword : char[]
    -keyAlias : String
    -keyStore : KeyStore
    -aesKey : SecretKey
    -KeyStoreManager()
    +getInstance() KeyStoreManager$
    +getAesKey() SecretKey
    -cargarKeyStore() void
    -extraerLlave() void
    -validarVariablesEntorno() void
    -limpiarPassword() void
  }

  class CryptoService {
    <<Service>>
    %% Cifrado simétrico AES-256 via javax.crypto.Cipher (JCA)
    %% Modo: AES/GCM/NoPadding — autenticado, resiste tampering
    %% IV (vector de inicialización): 12 bytes aleatorios por operación
    %% El IV se antepone al ciphertext: [12 bytes IV][ciphertext]
    %% Base64 para almacenar en VARCHAR Oracle
    -keyStoreManager : KeyStoreManager
    -ALGORITMO : String$
    -IV_LENGTH_BYTES : int$
    +cifrar(datos : byte[]) byte[]
    +descifrar(datosCifrados : byte[]) byte[]
    +cifrarTexto(texto : String) String
    +descifrarTexto(textoCifrado : String) String
    -generarIV() byte[]
    -construirCipher(modo : int, iv : byte[]) Cipher
  }

  class HashingService {
    <<Service>>
    %% Hashing unidireccional via java.security.MessageDigest (JCA)
    %% hashTemplate: SHA-256 del template biométrico para detectar tampering
    %% hashPassword: SHA-256 + salt para contraseñas de Administrador
    %% Salt: 16 bytes aleatorios, almacenado junto al hash separado por ":"
    %% Formato almacenado: "BASE64(salt):BASE64(hash)"
    -ALGORITMO_HASH : String$
    -SALT_LENGTH_BYTES : int$
    +hashTemplate(template : byte[]) String$
    +hashPassword(rawPassword : String) String$
    +verificarPassword(rawPassword : String, hashalmacenado : String) boolean$
    +verificarIntegridad(template : byte[], hashAlmacenado : String) boolean$
    -generarSalt() byte[]
    -calcularHash(datos : byte[], salt : byte[]) byte[]
  }

  class AuthMiddleware {
    <<Middleware>>
    %% Intercepta TODAS las rutas /api/admin/**
    %% authenticate(): verifica que el request tenga token válido
    %% requireAdmin(): además verifica que el rol sea SUPERADMIN
    %% Token se extrae del header: Authorization: Bearer <token>
    %% Se valida contra tabla SESIONES en Oracle (no es JWT)
    -sesionRepo : SesionRepository
    +authenticate(ctx : Context) void$
    +requireAdmin(ctx : Context) void$
    -validarToken(token : String) boolean
    -extraerToken(ctx : Context) String
    -rechazarAcceso(ctx : Context, mensaje : String) void
  }

  %% ══════════════════════════════════════════════════════════
  %% ENUMERACIÓN DE APOYO
  %% ══════════════════════════════════════════════════════════

  class TipoCifrado {
    <<enumeration>>
    %% Para indicar qué campo se está procesando en logs de auditoría
    PLANTILLA_BIOMETRICA
    FOTO_URL
    TOKEN_SESION
  }

  %% ══════════════════════════════════════════════════════════
  %% INTERFACES DE APOYO (contratos para testing)
  %% ══════════════════════════════════════════════════════════

  class ICryptoService {
    <<interface>>
    %% Permite mockear CryptoService en tests unitarios
    %% sin necesidad de tener el .jks físico en CI/CD
    +cifrarTexto(texto : String) String
    +descifrarTexto(textoCifrado : String) String
  }

  class IHashingService {
    <<interface>>
    %% Permite mockear HashingService en tests unitarios
    +hashPassword(rawPassword : String) String
    +verificarPassword(rawPassword : String, hash : String) boolean
    +verificarIntegridad(template : byte[], hash : String) boolean
  }

  %% ══════════════════════════════════════════════════════════
  %% RELACIONES
  %% ══════════════════════════════════════════════════════════

  ICryptoService <|.. CryptoService : implements
  IHashingService <|.. HashingService : implements
  KeyStoreManager *-- CryptoService : provee SecretKey a >
  CryptoService ..> TipoCifrado : usa en logs >
  CryptoService ..> KeyStoreManager : obtiene llave >

  %% ══════════════════════════════════════════════════════════
  %% CÓMO USAN ESTAS CLASES LOS OTROS SERVICIOS
  %% (referencia — estas clases están en feature/services)
  %% ══════════════════════════════════════════════════════════

  class PreRegistroService {
    <<Service — referencia>>
    %% RAMA: feature/services (Daniel Turizo)
    %% Usa CryptoService para cifrar antes de persistir
    %% Usa HashingService para generar hashIntegridadBiometrica
  }

  class BiometricService {
    <<Service — referencia>>
    %% RAMA: feature/services (Daniel Turizo)
    %% Usa CryptoService para descifrar template antes de enviar a Python
  }

  class AdminService {
    <<Service — referencia>>
    %% RAMA: feature/services (Daniel Turizo)
    %% Usa HashingService para verificar password en login
  }

  PreRegistroService ..> ICryptoService : inyectado >
  PreRegistroService ..> IHashingService : inyectado >
  BiometricService ..> ICryptoService : inyectado >
  AdminService ..> IHashingService : inyectado >
```
