# FASE 2: Definición de la Arquitectura del Proyecto ABIS-UPC

---

## 1. Arquitectura del Sistema — Diagrama de Paquetes por Capas

```
com.abisupc/
│
├── model/                     # CAPA DE DOMINIO — Entidades del negocio
│   ├── Entity.java            #   Clase abstracta base con ID genérico
│   ├── Votante.java           #   Ciudadano con datos personales y biométricos
│   ├── Candidato.java         #   Postulado a cargo de elección
│   ├── Eleccion.java          #   Proceso electoral con fechas y estado
│   ├── Voto.java              #   Voto emitido (no contiene identidad del votante)
│   ├── RegistroVoto.java      #   Auditoría: quién votó, cuándo, dónde (sin candidato)
│   ├── Sesion.java            #   Token de sesión de administrador
│   ├── Administrador.java     #   Usuario con privilegios del sistema
│   ├── Rol.java               #   Rol del votante (estudiante, docente, etc.)
│   ├── PuestoVotacion.java    #   Puesto/Mesa de votación física
│   ├── MesaJurado.java        #   Turno de mesa de jurado electoral
│   ├── Jurado.java            #   Asignación de jurado a mesa (sin herencia de Entity)
│   ├── EnrollRequest.java     #   DTO simple para petición de enrolamiento
│   ├── ScanResult.java        #   DTO con resultado del OCR
│   ├── EstadoVotante.java     #   Enum: PENDIENTE, EJERCIDO, INHABILITADO
│   └── EstadoEleccion.java    #   Enum: PROGRAMADA, EN_CURSO, CERRADA
│
├── dto/                       # CAPA DE TRANSFERENCIA DE DATOS
│   └── ApiResponse.java       #   Wrapper genérico para respuestas API
│
├── repository/                # CAPA DE ACCESO A DATOS — Patrón Repository
│   ├── Repository.java        #   Interfaz genérica CRUD <T>
│   ├── VotanteRepository.java
│   ├── CandidatoRepository.java
│   ├── EleccionRepository.java
│   ├── VotoRepository.java
│   ├── RegistroVotoRepository.java
│   ├── SesionRepository.java
│   ├── AdministradorRepository.java
│   ├── RolRepository.java
│   ├── PuestoVotacionRepository.java
│   ├── MesaJuradoRepository.java
│   └── JuradoRepository.java  #   (no implementa Repository, usa PK compuesta)
│
├── service/                   # CAPA DE LÓGICA DE NEGOCIO
│   ├── BiometricService.java  #   Orquestación de enrolamiento/verificación biométrica
│   └── OcrPythonService.java  #   Procesamiento OCR vía microservicio Python
│
├── controller/                # CAPA DE CONTROLADORES — Puntos de entrada HTTP
│   ├── EnrollController.java  #   POST /api/enroll
│   ├── VerifyController.java  #   POST /api/verify
│   ├── OcrController.java     #   POST /api/document/scan
│   └── TestController.java    #   GET /api/health
│
├── integration/               # CAPA DE INTEGRACIÓN — Clientes HTTP a microservicios
│   └── BiometricClient.java   #   Comunicación con FastAPI (:8001) y OCR (:8002)
│
├── security/                  # CAPA DE SEGURIDAD
│   └── AuthMiddleware.java    #   Validación de tokens de sesión
│
├── business/                  # CAPA DE REGLAS DE NEGOCIO
│   └── BusinessLogic.java     #   Procesamiento de datos
│
├── config/                    # CAPA DE CONFIGURACIÓN
│   └── AppConfig.java         #   Pool de conexiones HikariCP + variables entorno
│
└── server/                    # PUNTO DE ENTRADA — Orquestador Javalin
    └── AppServer.java         #   Configura rutas e inicia servidor en puerto 7000
```

---

## 2. Diagrama de Clases — Identificación de Clases

| # | Nombre de la Clase | Tipo | Descripción y Rol en el Sistema |
|---|---------------------|------|----------------------------------|
| 1 | **Entity** | Abstracta | Clase base que define el identificador `id` común a todas las entidades del dominio |
| 2 | **Votante** | Concreta | Representa al ciudadano con datos personales, biométricos, rol y puesto de votación |
| 3 | **Candidato** | Concreta | Representa un postulado a un cargo de elección con número de campaña |
| 4 | **Eleccion** | Concreta | Define un proceso electoral con fechas de inicio/fin y estado |
| 5 | **Voto** | Concreta | Registro del voto emitido (contiene solo candidato, rol y elección — sin identidad) |
| 6 | **RegistroVoto** | Concreta | Trazabilidad de auditoría: identidad del votante, puesto y elección (sin candidato) |
| 7 | **Sesion** | Concreta | Gestiona tokens de sesión activos de administradores |
| 8 | **Administrador** | Concreta | Usuario con credenciales para gestionar el sistema electoral |
| 9 | **Rol** | Concreta | Tipos de votante (ESTUDIANTE, DOCENTE, EGRESADO, ADMINISTRATIVO) con peso de voto |
| 10 | **PuestoVotacion** | Concreta | Ubicaciones físicas de votación (ciudad, sede, nombre) |
| 11 | **MesaJurado** | Concreta | Registro de turno de mesa de jurado electoral |
| 12 | **Jurado** | Concreta | Asignación de un votante como jurado en una mesa específica |
| 13 | **EnrollRequest** | Concreta | DTO para solicitud de enrolamiento biométrico |
| 14 | **ScanResult** | Concreta | DTO que encapsula el resultado del OCR (documento escaneado) |
| 15 | **EstadoVotante** | Enum | Estados posibles del voto: PENDIENTE, EJERCIDO, INHABILITADO |
| 16 | **EstadoEleccion** | Enum | Estados posibles de la elección: PROGRAMADA, EN_CURSO, CERRADA |
| 17 | **ApiResponse<T>** | Concreta | DTO genérico que envuelve respuestas JSON con success, message y data |
| 18 | **Repository<T>** | Interfaz | Define el contrato CRUD genérico para operaciones de persistencia |
| 19 | **VotanteRepository** | Concreta | Implementa acceso a tabla VOTANTES con métodos especializados |
| 20 | **CandidatoRepository** | Concreta | Implementa CRUD de CANDIDATOS con búsquedas por elección y cargo |
| 21 | **EleccionRepository** | Concreta | Implementa CRUD de ELECCIONES con control de estados |
| 22 | **VotoRepository** | Concreta | Implementa registro de VOTOS y cálculos de resultados |
| 23 | **RegistroVotoRepository** | Concreta | Implementa trazabilidad de REGISTRO_VOTOS |
| 24 | **SesionRepository** | Concreta | Implementa gestión de SESIONES con validación de tokens |
| 25 | **AdministradorRepository** | Concreta | Implementa CRUD de ADMINISTRADORES con autenticación |
| 26 | **RolRepository** | Concreta | Implementa CRUD de ROLES y consulta de peso de voto |
| 27 | **PuestoVotacionRepository** | Concreta | Implementa CRUD de PUESTOS_VOTACION |
| 28 | **MesaJuradoRepository** | Concreta | Implementa CRUD de MESA_JURADOS con control de turnos |
| 29 | **JuradoRepository** | Concreta | Implementa asignación de jurados a mesas (PK compuesta) |
| 30 | **BiometricService** | Concreta | Orquesta llamadas HTTP al microservicio FastAPI biométrico |
| 31 | **OcrPythonService** | Concreta | Procesa imágenes de documentos mediante microservicio OCR Python |
| 32 | **EnrollController** | Concreta | Recibe peticiones POST /api/enroll y delega al servicio biométrico |
| 33 | **VerifyController** | Concreta | Recibe peticiones POST /api/verify para verificar huella |
| 34 | **OcrController** | Concreta | Recibe POST /api/document/scan con imágenes y las envía al OCR |
| 35 | **TestController** | Concreta | Expone GET /api/health para monitoreo de todos los servicios |
| 36 | **BiometricClient** | Concreta | Cliente HTTP que se comunica con FastAPI biométrico y OCR |
| 37 | **AuthMiddleware** | Concreta | Middleware para validar tokens JWT en endpoints protegidos |
| 38 | **BusinessLogic** | Concreta | Capa de procesamiento de reglas de negocio |
| 39 | **AppConfig** | Concreta | Configura pool HikariCP y lee variables de entorno de Oracle |
| 40 | **AppServer** | Concreta | Orquestador principal que configura rutas Javalin y arranca el servidor |

---

## 3. Definición de Atributos, Métodos y Responsabilidades

---

### Clase: Entity
**Tipo:** Abstracta  
**Responsabilidad principal:** Proveer el identificador único `id` común a todas las entidades del dominio mediante herencia.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| id | Long | Identificador único auto-generado (mapeado a secuencia Oracle) |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| getId() | Long | Retorna el identificador único |
| setId(Long) | void | Asigna el identificador único |

---

### Clase: Votante
**Tipo:** Concreta (hereda de Entity)  
**Responsabilidad principal:** Representar a un ciudadano registrado en el censo electoral con todos los datos personales, biométricos y de ubicación.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| identificacion | String | Número único de documento de identidad |
| plantillaBiometrica | String | Template de huella dactilar cifrado (generado por AS608) |
| correo | String | Correo electrónico del votante |
| primerNombre | String | Primer nombre |
| segundoNombre | String | Segundo nombre (puede ser null/opcional) |
| primerApellido | String | Primer apellido |
| segundoApellido | String | Segundo apellido |
| estadoVoto | String | Estado actual: PENDIENTE, EJERCIDO o INHABILITADO |
| fotoUrl | String | URL/ruta de la foto extraída del documento de identidad |
| fechaConsentimiento | Timestamp | Fecha/hora en que el votante dio consentimiento biométrico |
| hashIntegridadBiometrica | String | Hash SHA-256 para verificar integridad del template |
| idRol | Long | FK hacia la tabla ROLES (identifica el tipo de votante) |
| idPuesto | Long | FK hacia PUESTOS_VOTACION (dónde vota) |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| getIdentificacion() | String | Retorna el número de documento |
| setIdentificacion(String) | void | Asigna el número de documento |
| getPlantillaBiometrica() | String | Retorna el template biométrico cifrado |
| setPlantillaBiometrica(String) | void | Asigna el template biométrico |
| getCorreo() | String | Retorna el correo electrónico |
| setCorreo(String) | void | Asigna el correo electrónico |
| getPrimerNombre() | String | Retorna el primer nombre |
| setPrimerNombre(String) | void | Asigna el primer nombre |
| getSegundoNombre() | String | Retorna el segundo nombre |
| setSegundoNombre(String) | void | Asigna el segundo nombre |
| getPrimerApellido() | String | Retorna el primer apellido |
| setPrimerApellido(String) | void | Asigna el primer apellido |
| getSegundoApellido() | String | Retorna el segundo apellido |
| setSegundoApellido(String) | void | Asigna el segundo apellido |
| getEstadoVoto() | String | Retorna el estado del voto |
| setEstadoVoto(String) | void | Asigna el estado del voto |
| getFotoUrl() | String | Retorna la URL de la foto |
| setFotoUrl(String) | void | Asigna la URL de la foto |
| getFechaConsentimiento() | Timestamp | Retorna la fecha de consentimiento |
| setFechaConsentimiento(Timestamp) | void | Asigna la fecha de consentimiento |
| getHashIntegridadBiometrica() | String | Retorna el hash SHA-256 |
| setHashIntegridadBiometrica(String) | void | Asigna el hash de integridad |
| getIdRol() | Long | Retorna el ID del rol |
| setIdRol(Long) | void | Asigna el ID del rol |
| getIdPuesto() | Long | Retorna el ID del puesto de votación |
| setIdPuesto(Long) | void | Asigna el ID del puesto |

---

### Clase: Candidato
**Tipo:** Concreta (hereda de Entity)  
**Responsabilidad principal:** Representar a una persona postulada a un cargo de elección popular dentro de una elección específica.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| primerNombre | String | Primer nombre del candidato |
| segundoNombre | String | Segundo nombre del candidato |
| primerApellido | String | Primer apellido del candidato |
| segundoApellido | String | Segundo apellido del candidato |
| numeroCampania | String | Número de campaña (identificador visual en tarjetón) |
| idEleccion | Long | FK hacia ELECCIONES (elección a la que pertenece) |
| cargo | String | Nombre del cargo al que aspira (Personería, Consejo, etc.) |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| getPrimerNombre() | String | Retorna el primer nombre |
| setPrimerNombre(String) | void | Asigna el primer nombre |
| getSegundoNombre() | String | Retorna el segundo nombre |
| setSegundoNombre(String) | void | Asigna el segundo nombre |
| getPrimerApellido() | String | Retorna el primer apellido |
| setPrimerApellido(String) | void | Asigna el primer apellido |
| getSegundoApellido() | String | Retorna el segundo apellido |
| setSegundoApellido(String) | void | Asigna el segundo apellido |
| getNumeroCampania() | String | Retorna el número de campaña |
| setNumeroCampania(String) | void | Asigna el número de campaña |
| getIdEleccion() | Long | Retorna el ID de la elección |
| setIdEleccion(Long) | void | Asigna el ID de la elección |
| getCargo() | String | Retorna el cargo aspirado |
| setCargo(String) | void | Asigna el cargo aspirado |

---

### Clase: Eleccion
**Tipo:** Concreta (hereda de Entity)  
**Responsabilidad principal:** Definir un proceso electoral incluyendo nombre, ventana de tiempo y estado actual.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| nombre | String | Nombre descriptivo de la elección |
| fechaHoraInicio | LocalDateTime | Fecha y hora de inicio programada |
| fechaHoraFin | LocalDateTime | Fecha y hora de cierre programada |
| estado | EstadoEleccion | Estado actual: PROGRAMADA, EN_CURSO, CERRADA |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| getNombre() | String | Retorna el nombre de la elección |
| setNombre(String) | void | Asigna el nombre |
| getFechaHoraInicio() | LocalDateTime | Retorna la fecha/hora de inicio |
| setFechaHoraInicio(LocalDateTime) | void | Asigna la fecha/hora de inicio |
| getFechaHoraFin() | LocalDateTime | Retorna la fecha/hora de cierre |
| setFechaHoraFin(LocalDateTime) | void | Asigna la fecha/hora de cierre |
| getEstado() | EstadoEleccion | Retorna el estado de la elección |
| setEstado(EstadoEleccion) | void | Asigna el estado |

---

### Clase: Voto
**Tipo:** Concreta (hereda de Entity)  
**Responsabilidad principal:** Registrar el voto emitido de forma anónima — solo almacena el candidato seleccionado, el rol y la elección, nunca la identidad del votante.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| idRol | Long | FK hacia ROLES (rol del votante al momento del voto) |
| idEleccion | Long | FK hacia ELECCIONES |
| idCandidato | Long | FK hacia CANDIDATOS (candidato seleccionado) |
| fechaHora | Timestamp | Momento exacto del registro del voto |
| pesoVotoAplicado | double | Peso del voto según el rol (ej: docente=2, estudiante=1) |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| getIdRol() | Long | Retorna el ID del rol |
| setIdRol(Long) | void | Asigna el ID del rol |
| getIdEleccion() | Long | Retorna el ID de la elección |
| setIdEleccion(Long) | void | Asigna el ID de la elección |
| getIdCandidato() | Long | Retorna el ID del candidato |
| setIdCandidato(Long) | void | Asigna el ID del candidato |
| getFechaHora() | Timestamp | Retorna la fecha/hora del voto |
| setFechaHora(Timestamp) | void | Asigna la fecha/hora |
| getPesoVotoAplicado() | double | Retorna el peso del voto |
| setPesoVotoAplicado(double) | void | Asigna el peso del voto |

---

### Clase: RegistroVoto
**Tipo:** Concreta (hereda de Entity)  
**Responsabilidad principal:** Registrar la trazabilidad de auditoría de quién votó, cuándo y en qué puesto — sin almacenar el candidato seleccionado para preservar el secreto del voto.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| fechaHora | LocalDateTime | Fecha y hora del registro |
| identificacion | String | Identificación del votante (para auditoría) |
| idPuesto | Long | FK hacia PUESTOS_VOTACION (dónde votó) |
| idEleccion | Long | FK hacia ELECCIONES |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| getFechaHora() | LocalDateTime | Retorna la fecha/hora de registro |
| setFechaHora(LocalDateTime) | void | Asigna la fecha/hora |
| getIdentificacion() | String | Retorna la identificación del votante |
| setIdentificacion(String) | void | Asigna la identificación |
| getIdPuesto() | Long | Retorna el ID del puesto |
| setIdPuesto(Long) | void | Asigna el ID del puesto |
| getIdEleccion() | Long | Retorna el ID de la elección |
| setIdEleccion(Long) | void | Asigna el ID de la elección |

---

### Clase: Sesion
**Tipo:** Concreta (hereda de Entity)  
**Responsabilidad principal:** Gestionar tokens de sesión de administradores autenticados con control de expiración.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| token | String | Token único de sesión (UUID) |
| fechaInicio | LocalDateTime | Fecha/hora de inicio de sesión |
| idAdministrador | Long | FK hacia ADMINISTRADORES (propietario de la sesión) |
| fechaFin | LocalDateTime | Fecha/hora de cierre de sesión (null si activa) |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| getToken() | String | Retorna el token de sesión |
| setToken(String) | void | Asigna el token |
| getFechaInicio() | LocalDateTime | Retorna la fecha de inicio |
| setFechaInicio(LocalDateTime) | void | Asigna la fecha de inicio |
| getIdAdministrador() | Long | Retorna el ID del administrador |
| setIdAdministrador(Long) | void | Asigna el ID del administrador |
| getFechaFin() | LocalDateTime | Retorna la fecha de cierre |
| setFechaFin(LocalDateTime) | void | Asigna la fecha de cierre |

---

### Clase: Administrador
**Tipo:** Concreta (hereda de Entity)  
**Responsabilidad principal:** Representar a un usuario con credenciales y privilegios para gestionar el sistema electoral.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| usuario | String | Nombre de usuario único para inicio de sesión |
| passwordHash | String | Hash de la contraseña (almacenado de forma segura) |
| nombre | String | Nombre completo del administrador |
| correo | String | Correo electrónico del administrador |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| getUsuario() | String | Retorna el nombre de usuario |
| setUsuario(String) | void | Asigna el nombre de usuario |
| getPasswordHash() | String | Retorna el hash de contraseña |
| setPasswordHash(String) | void | Asigna el hash de contraseña |
| getNombre() | String | Retorna el nombre completo |
| setNombre(String) | void | Asigna el nombre completo |
| getCorreo() | String | Retorna el correo electrónico |
| setCorreo(String) | void | Asigna el correo electrónico |

---

### Clase: Rol
**Tipo:** Concreta (hereda de Entity)  
**Responsabilidad principal:** Definir los tipos de votante y el peso de su voto en la ponderación de resultados.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| nombre | String | Nombre del rol (ESTUDIANTE, DOCENTE, EGRESADO, ADMINISTRATIVO) |
| pesoVoto | double | Peso ponderado del voto para este rol |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| getNombre() | String | Retorna el nombre del rol |
| setNombre(String) | void | Asigna el nombre del rol |
| getPesoVoto() | double | Retorna el peso del voto |
| setPesoVoto(double) | void | Asigna el peso del voto |

---

### Clase: PuestoVotacion
**Tipo:** Concreta (hereda de Entity)  
**Responsabilidad principal:** Representar una ubicación física donde se instala una estación de votación.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| ciudad | String | Ciudad donde se ubica el puesto |
| sede | String | Sede universitaria (SEDE CENTRAL, SEDE RIO, etc.) |
| nombrePuesto | String | Nombre descriptivo del puesto |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| getCiudad() | String | Retorna la ciudad |
| setCiudad(String) | void | Asigna la ciudad |
| getSede() | String | Retorna la sede |
| setSede(String) | void | Asigna la sede |
| getNombrePuesto() | String | Retorna el nombre del puesto |
| setNombrePuesto(String) | void | Asigna el nombre del puesto |

---

### Clase: MesaJurado
**Tipo:** Concreta (hereda de Entity)  
**Responsabilidad principal:** Registrar el turno de los jurados electorales en una mesa de votación con hora de ingreso, salida y cargo.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| horaIngreso | LocalDateTime | Hora de ingreso del jurado a la mesa |
| horaSalida | LocalDateTime | Hora de salida (null si sigue activo) |
| cargo | String | Cargo del jurado en la mesa |
| idPuesto | Long | FK hacia PUESTOS_VOTACION |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| getHoraIngreso() | LocalDateTime | Retorna la hora de ingreso |
| setHoraIngreso(LocalDateTime) | void | Asigna la hora de ingreso |
| getHoraSalida() | LocalDateTime | Retorna la hora de salida |
| setHoraSalida(LocalDateTime) | void | Asigna la hora de salida |
| getCargo() | String | Retorna el cargo |
| setCargo(String) | void | Asigna el cargo |
| getIdPuesto() | Long | Retorna el ID del puesto |
| setIdPuesto(Long) | void | Asigna el ID del puesto |

---

### Clase: Jurado
**Tipo:** Concreta (NO hereda de Entity — usa clave primaria compuesta)  
**Responsabilidad principal:** Asignar un votante como jurado electoral en una mesa específica con un cargo determinado.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| idMesa | Long | ID de la mesa de jurados (parte de PK compuesta) |
| identificacion | String | Identificación del votante asignado como jurado |
| fechaAsignacion | LocalDate | Fecha en que fue asignado |
| cargo | String | Cargo que desempeña en la mesa |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| getIdMesa() | Long | Retorna el ID de la mesa |
| setIdMesa(Long) | void | Asigna el ID de la mesa |
| getIdentificacion() | String | Retorna la identificación del jurado |
| setIdentificacion(String) | void | Asigna la identificación |
| getFechaAsignacion() | LocalDate | Retorna la fecha de asignación |
| setFechaAsignacion(LocalDate) | void | Asigna la fecha |
| getCargo() | String | Retorna el cargo |
| setCargo(String) | void | Asigna el cargo |

---

### Clase: EnrollRequest
**Tipo:** Concreta (DTO simple)  
**Responsabilidad principal:** Serializar/deserializar la petición HTTP de enrolamiento biométrico con identificación y bandera de re-enrolamiento.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| identificacion | String | Número de documento a enrolar |
| re_enroll | boolean | Si es true, reemplaza la huella existente |

---

### Clase: ScanResult
**Tipo:** Concreta (DTO)  
**Responsabilidad principal:** Encapsular el resultado completo del escaneo OCR de un documento de identidad, incluyendo tipo de documento, confianza y datos extraídos.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| documentType | String | Tipo de documento detectado |
| status | String | Estado del procesamiento |
| overallConfidence | Double | Confianza general del OCR |
| classificationConfidence | Double | Confianza de la clasificación del documento |
| errors | List<String> | Lista de errores encontrados |
| numero | String | Número de documento (OCR crudo) |
| nombres | String | Nombres completos (OCR crudo) |
| apellidos | String | Apellidos completos (OCR crudo) |
| primerNombre | String | Primer nombre estructurado |
| segundoNombre | String | Segundo nombre estructurado |
| primerApellido | String | Primer apellido estructurado |
| segundoApellido | String | Segundo apellido estructurado |
| fechaNacimiento | String | Fecha de nacimiento extraída |
| fechaExpiracion | String | Fecha de expiración del documento |
| sexo | String | Sexo (M/F) |
| lugarNacimiento | String | Lugar de nacimiento |
| fieldConfidence | Map<String, Double> | Confianza por campo extraído |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| Getters/Setters | Varios | Acceso y modificación de todos los atributos |

---

### Clase: ApiResponse<T>
**Tipo:** Concreta (DTO genérico)  
**Responsabilidad principal:** Envolver respuestas API en un formato estandarizado con indicador de éxito, mensaje y datos genéricos.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| success | boolean | Indica si la operación fue exitosa |
| message | String | Mensaje descriptivo del resultado |
| data | T | Datos de respuesta genéricos |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| constructores | - | Constructor vacío y constructor con todos los campos |
| isSuccess() | boolean | Retorna si fue exitoso |
| setSuccess(boolean) | void | Asigna el estado de éxito |
| getMessage() | String | Retorna el mensaje |
| setMessage(String) | void | Asigna el mensaje |
| getData() | T | Retorna los datos genéricos |
| setData(T) | void | Asigna los datos genéricos |

---

### Clase: Repository<T> (Interfaz)
**Tipo:** Interfaz genérica  
**Responsabilidad principal:** Definir el contrato abstracto para operaciones CRUD estándar que cualquier repositorio concreto debe implementar.

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| findById(Long) | Optional<T> | Busca una entidad por su identificador único |
| findAll() | List<T> | Retorna todas las entidades del tipo T |
| save(T) | void | Persiste una nueva entidad en la base de datos |
| update(T) | void | Actualiza una entidad existente |
| delete(Long) | void | Elimina una entidad por su identificador |

---

### Clase: VotanteRepository
**Tipo:** Concreta (implementa Repository<Votante>)  
**Responsabilidad principal:** Gestionar el acceso a datos de la tabla VOTANTES en Oracle con operaciones especializadas como búsqueda por identificación, control de estado, actualización de plantilla biométrica y anonimización.

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| findById(Long) | Optional<Votante> | Busca votante por ID numérico |
| findAll() | List<Votante> | Lista todos los votantes ordenados por ID |
| save(Votante) | void | Inserta nuevo votante con secuencia Oracle |
| update(Votante) | void | Actualiza datos del votante |
| delete(Long) | void | Elimina votante (con control de integridad FK) |
| findByIdentificacion(String) | Optional<Votante> | Busca votante por número de documento |
| findByIdRol(Long) | List<Votante> | Lista votantes de un rol específico |
| findByIdPuesto(Long) | List<Votante> | Lista votantes de un puesto de votación |
| estaHabilitado(String) | boolean | Verifica si el votante tiene estado PENDIENTE |
| actualizarEstado(String, String) | void | Cambia el estado del voto |
| actualizarPlantilla(String, String, String) | void | Actualiza template biométrico y hash |
| actualizarFoto(String, String) | void | Actualiza la URL de la foto |
| anonimizarDatosBiometricos(String) | void | Elimina datos biométricos post-elección |

---

### Clase: CandidatoRepository
**Tipo:** Concreta (implementa Repository<Candidato>)  
**Responsabilidad principal:** Gestionar el acceso a datos de la tabla CANDIDATOS con búsquedas por elección y cargo.

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| findById(Long) | Optional<Candidato> | Busca candidato por ID |
| findAll() | List<Candidato> | Lista todos los candidatos |
| save(Candidato) | void | Inserta nuevo candidato |
| update(Candidato) | void | Actualiza datos del candidato |
| delete(Long) | void | Elimina candidato (con control de votos asociados) |
| findByEleccion(Long) | List<Candidato> | Lista candidatos de una elección |
| findByCargo(Long, String) | List<Candidato> | Lista candidatos por elección y cargo |
| getCargosDistintos(Long) | List<String> | Obtiene cargos distintos de una elección |

---

### Clase: EleccionRepository
**Tipo:** Concreta (implementa Repository<Eleccion>)  
**Responsabilidad principal:** Gestionar el acceso a datos de la tabla ELECCIONES con control de estados y búsqueda de elección activa.

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| findById(Long) | Optional<Eleccion> | Busca elección por ID |
| findAll() | List<Eleccion> | Lista todas las elecciones |
| save(Eleccion) | void | Crea nueva elección |
| update(Eleccion) | void | Actualiza datos de la elección |
| delete(Long) | void | Elimina elección (con control de integridad) |
| findActiva() | Optional<Eleccion> | Busca la elección en estado EN_CURSO |
| findByEstado(String) | List<Eleccion> | Lista elecciones por estado |
| actualizarEstado(Long, String) | void | Cambia el estado de la elección |

---

### Clase: VotoRepository
**Tipo:** Concreta (implementa Repository<Voto>)  
**Responsabilidad principal:** Gestionar el registro de votos y proporcionar cálculos de resultados electorales (conteo simple y ponderado).

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| findById(Long) | Optional<Voto> | Busca voto por ID |
| findAll() | List<Voto> | Lista todos los votos |
| save(Voto) | void | Inserta nuevo voto |
| update(Voto) | void | Actualiza datos del voto |
| delete(Long) | void | Elimina voto |
| findByEleccion(Long) | List<Voto> | Lista votos de una elección |
| countByCandidato(Long) | int | Cuenta votos de un candidato |
| obtenerResultados(Long) | Map<Long, Integer> | Resultados: candidato → conteo simple |
| obtenerResultadosPonderados(Long) | Map<Long, Double> | Resultados: candidato → suma ponderada |
| obtenerResultadosPorRol(Long) | Map<String, Integer> | Votos agrupados por rol |

---

### Clase: RegistroVotoRepository
**Tipo:** Concreta (implementa Repository<RegistroVoto>)  
**Responsabilidad principal:** Gestionar el registro de auditoría de votación para trazabilidad forense.

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| findById(Long) | Optional<RegistroVoto> | Busca registro por ID |
| findAll() | List<RegistroVoto> | Lista todos los registros |
| save(RegistroVoto) | void | Crea nuevo registro de voto |
| update(RegistroVoto) | void | Actualiza registro |
| delete(Long) | void | Elimina registro |
| yaVoto(String, Long) | boolean | Verifica si un votante ya votó en una elección |
| findByEleccion(Long) | List<RegistroVoto> | Lista registros de una elección |
| findByIdentificacion(String) | List<RegistroVoto> | Historial de votos de un votante |
| countByPuesto(Long, Long) | int | Cuenta votos emitidos en un puesto |

---

### Clase: SesionRepository
**Tipo:** Concreta (implementa Repository<Sesion>)  
**Responsabilidad principal:** Gestionar tokens de sesión de administradores con validación de sesiones activas y expiración.

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| findById(Long) | Optional<Sesion> | Busca sesión por ID |
| findAll() | List<Sesion> | Lista todas las sesiones |
| save(Sesion) | void | Crea nueva sesión con token |
| update(Sesion) | void | Actualiza sesión (ej: cierre) |
| delete(Long) | void | Elimina sesión |
| findByToken(String) | Optional<Sesion> | Busca sesión activa por token |
| findActivaByAdmin(Long) | Optional<Sesion> | Busca sesión activa de un administrador |
| invalidarToken(String) | void | Marca sesión como cerrada (fecha_fin) |
| limpiarSesionesExpiradas() | void | Elimina sesiones con más de 30 días |

---

### Clase: AdministradorRepository
**Tipo:** Concreta (implementa Repository<Administrador>)  
**Responsabilidad principal:** Gestionar el acceso a datos de administradores del sistema con métodos de autenticación.

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| findById(Long) | Optional<Administrador> | Busca administrador por ID |
| findAll() | List<Administrador> | Lista todos los administradores |
| save(Administrador) | void | Crea nuevo administrador |
| update(Administrador) | void | Actualiza datos del administrador |
| delete(Long) | void | Elimina administrador (con control de sesiones) |
| findByUsuario(String) | Optional<Administrador> | Busca por nombre de usuario (login) |
| findByCorreo(String) | Optional<Administrador> | Busca por correo electrónico |

---

### Clase: RolRepository
**Tipo:** Concreta (implementa Repository<Rol>)  
**Responsabilidad principal:** Gestionar los roles de votante y proporcionar el peso de voto para cálculos ponderados.

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| findById(Long) | Optional<Rol> | Busca rol por ID |
| findAll() | List<Rol> | Lista todos los roles |
| save(Rol) | void | Crea nuevo rol |
| update(Rol) | void | Actualiza rol |
| delete(Long) | void | Elimina rol (con control de votantes asignados) |
| findByNombre(String) | Optional<Rol> | Busca rol por nombre |
| estaEnUso(Long) | boolean | Verifica si hay votantes con ese rol |
| getPesoVoto(Long) | double | Obtiene el peso del voto para un rol |

---

### Clase: PuestoVotacionRepository
**Tipo:** Concreta (implementa Repository<PuestoVotacion>)  
**Responsabilidad principal:** Gestionar los puestos de votación física.

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| findById(Long) | Optional<PuestoVotacion> | Busca puesto por ID |
| findAll() | List<PuestoVotacion> | Lista todos los puestos |
| save(PuestoVotacion) | void | Crea nuevo puesto |
| update(PuestoVotacion) | void | Actualiza datos del puesto |
| delete(Long) | void | Elimina puesto (con control de dependencias) |

---

### Clase: MesaJuradoRepository
**Tipo:** Concreta (implementa Repository<MesaJurado>)  
**Responsabilidad principal:** Gestionar los turnos de mesa de los jurados electorales.

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| findById(Long) | Optional<MesaJurado> | Busca mesa por ID |
| findAll() | List<MesaJurado> | Lista todas las mesas |
| save(MesaJurado) | void | Crea nueva mesa |
| update(MesaJurado) | void | Actualiza datos de la mesa |
| delete(Long) | void | Elimina mesa (con control de jurados asociados) |
| findByPuesto(Long) | List<MesaJurado> | Lista mesas de un puesto |
| findActivas() | List<MesaJurado> | Lista mesas sin hora de salida |

---

### Clase: JuradoRepository
**Tipo:** Concreta (NO implementa Repository<T> — usa PK compuesta mesa+votante)  
**Responsabilidad principal:** Gestionar la asignación de votantes como jurados en mesas electorales.

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| save(Jurado) | void | Asigna un votante como jurado a una mesa |
| findByMesa(Long) | List<Jurado> | Lista jurados de una mesa |
| findByIdentificacion(String) | List<Jurado> | Busca asignaciones por identificación |
| esJurado(String) | boolean | Verifica si un votante es jurado |
| asignarAMesa(String, Long, String) | void | Reasigna jurado a otra mesa |

---

### Clase: BiometricService
**Tipo:** Concreta  
**Responsabilidad principal:** Orquestar la comunicación HTTP con el microservicio FastAPI biométrico (puerto 8001) y el NativeService C# (puerto 8765) para enrolamiento, verificación y monitoreo.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| FASTAPI_URL | String | URL base del microservicio biométrico (variable de entorno BIOMETRIC_SERVICE_URL) |
| NATIVE_URL | String | URL base del servicio nativo C# (variable de entorno NATIVE_SERVICE_URL) |
| client | HttpClient | Cliente HTTP configurado con HTTP/1.1 |
| mapper | ObjectMapper | Mapeador JSON Jackson |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| enroll(String, boolean) | JsonNode | Envía petición de enrolamiento biométrico al FastAPI |
| verify() | JsonNode | Solicita verificación de huella dactilar al sensor AS608 |
| servicesStatus() | JsonNode | Consulta estado de salud de FastAPI y NativeService |

---

### Clase: OcrPythonService
**Tipo:** Concreta  
**Responsabilidad principal:** Procesar imágenes de documentos de identidad mediante el microservicio OCR Python (puerto 8002) construyendo peticiones multipart/form-data.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| pythonApiUrl | String | URL base del microservicio OCR (variable de entorno OCR_SERVICE_URL) |
| httpClient | HttpClient | Cliente HTTP con timeout de 10s conexión y 60s petición |
| objectMapper | ObjectMapper | Mapeador JSON Jackson |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| scan(byte[], byte[], String) | ScanResult | Envía imágenes frontal/trasera al OCR y retorna datos estructurados |
| buildMultipart(String, byte[], byte[], String) | byte[] | Construye cuerpo multipart para petición HTTP |

---

### Clase: EnrollController
**Tipo:** Concreta  
**Responsabilidad principal:** Recibir peticiones HTTP POST /api/enroll, validar los datos de entrada y delegar el enrolamiento biométrico al BiometricService.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| service | BiometricService | Instancia estática del servicio biométrico |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| enroll(Context) | void | Procesa POST /api/enroll: valida identificación, llama a service.enroll() y retorna JSON |

---

### Clase: VerifyController
**Tipo:** Concreta  
**Responsabilidad principal:** Recibir peticiones HTTP POST /api/verify y delegar la verificación de huella al servicio biométrico.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| service | BiometricService | Instancia estática del servicio biométrico |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| verify(Context) | void | Procesa POST /api/verify: llama a service.verify() y retorna JSON con resultado de match |

---

### Clase: OcrController
**Tipo:** Concreta  
**Responsabilidad principal:** Recibir peticiones HTTP POST /api/document/scan con imágenes de documento y tipo, y delegar el procesamiento al OcrPythonService.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| ocrService | OcrPythonService | Instancia estática del servicio OCR |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| scan(Context) | void | Procesa POST /api/document/scan: recibe archivos front/back y doc_type, llama a ocrService.scan() y retorna JSON |

---

### Clase: TestController
**Tipo:** Concreta  
**Responsabilidad principal:** Proporcionar un endpoint de salud (health check) que verifica el estado de todos los servicios del sistema (backend, biométrico, OCR).

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| register(Javalin) | void | Registra GET /api/health en la app Javalin, verifica BiometricClient.isAlive() y isOcrAlive() |

---

### Clase: BiometricClient
**Tipo:** Concreta  
**Responsabilidad principal:** Actuar como puente de integración entre el backend Java y los microservicios Python (biométrico :8001 y OCR :8002) utilizando el cliente HTTP Unirest.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| BIOMETRIC_BASE_URL | String | URL del microservicio biométrico (variable de entorno o localhost:8001) |
| OCR_BASE_URL | String | URL del microservicio OCR (variable de entorno o localhost:8002) |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| scanDocument(InputStream, String) | String | Envía imagen al OCR y retorna JSON con datos extraídos |
| enrollVoter(String, boolean) | String | Envía identificación al servicio de enrolamiento biométrico |
| verifyFingerprint() | String | Solicita verificación de huella al sensor |
| registerVote(String) | String | Registra que el voto fue ejercido en el biométrico |
| isAlive() | boolean | Verifica conectividad con servicio biométrico |
| isOcrAlive() | boolean | Verifica conectividad con servicio OCR |

---

### Clase: AuthMiddleware
**Tipo:** Concreta  
**Responsabilidad principal:** Interceptar peticiones HTTP a endpoints protegidos y validar la autenticidad del token de sesión antes de permitir el acceso.

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| authenticate(Context) | void | Valida el token JWT en el header de la petición (placeholder para implementación completa) |

---

### Clase: BusinessLogic
**Tipo:** Concreta  
**Responsabilidad principal:** Contener la lógica de procesamiento de reglas de negocio del sistema (capa de abstracción para futuras validaciones complejas).

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| processData(String) | String | Procesa datos de entrada y retorna resultado transformado |

---

### Clase: AppConfig
**Tipo:** Concreta  
**Responsabilidad principal:** Configurar el pool de conexiones HikariCP a la base de datos Oracle, leyendo las credenciales desde variables de entorno del sistema.

| Atributo | Tipo de Dato | Descripción |
|----------|---------------|-------------|
| dataSource | HikariDataSource | Pool de conexiones configurado con máximo 10 conexiones |

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| getConnection() | Connection | Obtiene una conexión del pool HikariCP |

---

### Clase: AppServer
**Tipo:** Concreta (Punto de entrada)  
**Responsabilidad principal:** Orquestar el inicio del servidor Javalin en el puerto 7000, configurar CORS, rutas estáticas y registrar todos los controladores del sistema.

| Método | Tipo de Retorno | Descripción |
|--------|-----------------|-------------|
| main(String[]) | void | Punto de entrada: crea Javalin, habilita CORS, registra rutas (/api/health, /api/status, /api/enroll, /api/verify, /api/document/scan) e inicia servidor |

---

## 4. Relaciones entre Clases

```
                        ┌─────────────────────────────────────────────────────┐
                        │                   com.abisupc                        │
                        └─────────────────────────────────────────────────────┘

═══ HERENCIA (extends) ═══════════════════════════════════════════════════════════

                           ┌──────────────┐
                           │    Entity     │  (Abstracta)
                           │   - id: Long  │
                           └──────┬───────┘
                                  │
          ┌───────────────────────┼───────────────────────┬──────────────────┐
          │                       │                       │                  │
    ┌─────┴─────┐         ┌──────┴──────┐         ┌──────┴──────┐    ┌────┴────┐
    │  Votante  │         │  Candidato  │         │  Eleccion   │    │  Voto   │
    └───────────┘         └─────────────┘         └─────────────┘    └─────────┘

    ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    ┌──────────────┐
    │RegistroVoto  │     │   Sesion     │     │Administrador │    │     Rol      │
    └──────────────┘     └──────────────┘     └──────────────┘    └──────────────┘

    ┌──────────────┐     ┌──────────────┐
    │PuestoVotacion│     │ MesaJurado   │
    └──────────────┘     └──────────────┘

═══ IMPLEMENTACIÓN (implements) ═════════════════════════════════════════════════

    ┌──────────────────────┐
    │  Repository<T>       │  (Interfaz genérica)
    │  + findById()        │
    │  + findAll()         │
    │  + save()            │
    │  + update()          │
    │  + delete()          │
    └──────────┬───────────┘
               │ <<implementa>>
    ┌──────────┼───────────┐
    │          │           │
    ▼          ▼           ▼
  Votante    Candidato    Eleccion    Voto    RegistroVoto
  Repository Repository  Repository  Repository  Repository

  Sesion    Administrador    Rol      PuestoVotacion  MesaJurado
  Repository  Repository  Repository   Repository     Repository

═══ ASOCIACIONES CLAVE ═══════════════════════════════════════════════════════════

    Votante ────→ Rol              (Muchos a Uno: cada votante tiene un rol)
    Votante ────→ PuestoVotacion   (Muchos a Uno: cada votante tiene un puesto)
    Candidato ──→ Eleccion         (Muchos a Uno: candidatos pertenecen a elección)
    Voto ────────→ Candidato       (Muchos a Uno: voto selecciona un candidato)
    Voto ────────→ Rol             (Muchos a Uno: voto tiene el rol del votante)
    Voto ────────→ Eleccion        (Muchos a Uno: voto pertenece a elección)
    RegistroVoto → Eleccion        (Muchos a Uno: registro de auditoría)
    RegistroVoto → PuestoVotacion  (Muchos a Uno: dónde se votó)
    Sesion ──────→ Administrador   (Muchos a Uno: sesiones de un admin)
    MesaJurado ─→ PuestoVotacion   (Muchos a Uno: mesas de un puesto)
    Jurado ──────→ MesaJurado      (Muchos a Uno: jurados de una mesa)
    Jurado ──────→ Votante         (Muchos a Uno: un votante puede ser jurado)

═══ COMPOSICIÓN / AGREGACIÓN ═════════════════════════════════════════════════════

    AppServer ◈── EnrollController       (Agregación)
    AppServer ◈── VerifyController       (Agregación)
    AppServer ◈── OcrController          (Agregación)
    AppConfig ◆── HikariDataSource       (Composición: ciclo de vida ligado)

═══ DEPENDENCIA (uses) ═══════════════════════════════════════════════════════════

    EnrollController ──→ BiometricService
    VerifyController ──→ BiometricService
    OcrController ─────→ OcrPythonService
    TestController ────→ BiometricClient
    BiometricService ──→ BiometricClient
    BiometricService ──→ HttpClient
    BiometricService ──→ ObjectMapper
    OcrPythonService ──→ HttpClient
    OcrPythonService ──→ ObjectMapper
    Todos los repos ───→ AppConfig (getConnection())
    AppConfig ─────────→ HikariCP
```

---

## 5. Diagrama de Paquetes (Arquitectura en Capas)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      CAPA DE PRESENTACIÓN (HTTP)                        │
│  ┌────────────────────┐  ┌──────────────────┐  ┌──────────────────┐    │
│  │ EnrollController   │  │ VerifyController  │  │  OcrController   │    │
│  │ POST /api/enroll   │  │ POST /api/verify  │  │ /api/document/   │    │
│  └─────────┬──────────┘  └────────┬─────────┘  │ scan             │    │
│            │                       │             └────────┬─────────┘    │
│  ┌───────────────────────────────────────────────────────┐ │             │
│  │              TestController                           │ │             │
│  │              GET /api/health                          │ │             │
│  └───────────────────────────────────────────────────────┘ │             │
└─────────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      CAPA DE LÓGICA DE NEGOCIO (Service)                │
│  ┌─────────────────────────────┐  ┌────────────────────────────────┐   │
│  │     BiometricService        │  │      OcrPythonService          │   │
│  │  - enroll()                 │  │  - scan(front, back, docType)  │   │
│  │  - verify()                 │  │  - buildMultipart()            │   │
│  │  - servicesStatus()         │  │                                │   │
│  └─────────────┬───────────────┘  └────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      CAPA DE INTEGRACIÓN (Integration)                  │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │                    BiometricClient                              │    │
│  │  - scanDocument() → OCR :8002                                  │    │
│  │  - enrollVoter()  → Biometric :8001                            │    │
│  │  - verifyFingerprint() → Biometric :8001                       │    │
│  │  - registerVote() → Biometric :8001                            │    │
│  │  - isAlive() / isOcrAlive()                                    │    │
│  └────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      CAPA DE ACCESO A DATOS (Repository)                │
│  Interface                                                             │
│  ┌──────────────────────────────────────────────────────────┐          │
│  │              Repository<T> (Interfaz genérica)            │          │
│  │  + findById() | + findAll() | + save() | + update() | + delete()   │
│  └──────────────────────────────────────────────────────────┘          │
│  Implementaciones:                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ Votante  │ │Candidato │ │Eleccion  │ │  Voto    │ │Registro  │   │
│  │ Repo     │ │ Repo     │ │ Repo     │ │  Repo    │ │Voto Repo │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ Sesion   │ │Administr │ │  Rol     │ │ Puesto   │ │MesaJurado│   │
│  │ Repo     │ │Repo      │ │  Repo    │ │ Votacion │ │ Repo     │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘   │
│  ┌──────────┐                                                        │
│  │ Jurado   │ (no implementa interfaz)                                │
│  │ Repo     │                                                        │
│  └──────────┘                                                        │
└─────────────────────────────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      CAPA DE CONFIGURACIÓN / INFRAESTRUCTURA            │
│  ┌─────────────────────────────────────────────────────────────┐       │
│  │                    AppConfig                                │       │
│  │  Pool HikariCP (max 10 conexiones)                          │       │
│  │  Variables: ABIS_DB_URL, ABIS_DB_USER, ABIS_DB_PASSWORD     │       │
│  └─────────────────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────────────┘
                  │
                  ▼
            ┌──────────┐
            │  Oracle  │
            │  XE 18c  │
            └──────────┘
```

---

## 6. Principios GRASP Aplicados

| Patrón GRASP | Clase(s) donde se aplica | Razón de diseño |
|-------------|---------------------------|-----------------|
| **Creator** | VotanteRepository, CandidatoRepository, etc. | Cada repositorio crea instancias de su entidad correspondiente al mapear filas de ResultSet a objetos Java, siguiendo el criterio de que quien contiene los datos debe crear el objeto. |
| **Controller** | EnrollController, VerifyController, OcrController | Estos controladores son los puntos de entrada del sistema que reciben eventos externos (peticiones HTTP) y coordinan las operaciones delegando en los servicios, sin implementar lógica de negocio. |
| **Expert** | VotanteRepository, VotoRepository | Cada repositorio es el experto en la información que gestiona: VotanteRepository conoce la estructura de la tabla VOTANTES y sus consultas específicas; VotoRepository sabe cómo calcular resultados electorales. |
| **Low Coupling** | Repository<T> (interfaz) y BiometricClient | La interfaz Repository<T> desacopla la capa de servicios de las implementaciones concretas de repositorio. BiometricClient desacopla los controladores de los detalles de comunicación HTTP con Python. |
| **High Cohesion** | EnrollController, EleccionRepository, AppConfig | Cada clase tiene una responsabilidad única y bien definida: EnrollController solo maneja HTTP, EleccionRepository solo accede a la tabla ELECCIONES, AppConfig solo configura la conexión a base de datos. |
| **Polymorphism** | Repository<T> | La interfaz Repository<T> permite que diferentes tipos de repositorio (Votante, Candidato, Elección, etc.) implementen el mismo contrato, permitiendo al cliente tratar a todos de forma polimórfica. |
| **Pure Fabrication** | BiometricService, OcrPythonService | Estas clases no representan conceptos del dominio real sino que son fabricaciones puras que encapsulan la comunicación HTTP con microservicios externos, evitando que esa responsabilidad recaiga sobre entidades del dominio. |
| **Indirection** | BiometricClient | Actúa como intermediario entre el backend Java y los microservicios Python, aislando a los controladores de los detalles de implementación de los servicios externos. |
| **Protected Variations** | Repository<T>, Entity | La interfaz Repository<T> protege al sistema de variaciones en la tecnología de persistencia. Entity protege de cambios en la estructura del identificador de entidades. |

---

## 7. Principios SOLID Aplicados

| Principio | Clase/Relación | Argumentación |
|-----------|----------------|----------------|
| **SRP** (Single Responsibility) | EnrollController | Tiene una única responsabilidad: recibir y validar peticiones HTTP de enrolamiento. No accede a base de datos ni ejecuta lógica biométrica. |
| **SRP** | BiometricService | Su única responsabilidad es orquestar la comunicación HTTP con el microservicio FastAPI. No maneja HTTP entrante ni acceso a datos. |
| **SRP** | VotanteRepository | Responsable únicamente del acceso a datos de la tabla VOTANTES. Si cambia la estructura de la tabla, solo esta clase se modifica. |
| **SRP** | OcrPythonService | Responsable exclusivamente de construir peticiones multipart y comunicarse con el microservicio OCR. No realiza procesamiento de imágenes. |
| **SRP** | VotoRepository | Se encarga solo de las operaciones sobre la tabla VOTOS y los cálculos de resultados electorales. No mezcla lógica de otras entidades. |
| **OCP** (Open/Closed) | Repository<T> | Abierto para extensión: se pueden crear nuevos repositorios (VotanteRepository, CandidatoRepository, etc.) implementando la interfaz sin modificar el contrato base. Cerrado para modificación: la interfaz una vez definida no cambia. |
| **OCP** | BiometricClient | Abierto para extensión: se pueden añadir nuevos métodos de comunicación (scan, enroll, verify, vote, health) sin alterar la estructura existente de la clase. |
| **OCP** | Entity | Permite extender nuevas entidades (Votante, Candidato, Elección, etc.) heredando de Entity sin modificar la clase base. |
| **LSP** (Liskov Substitution) | VotanteRepository | Puede ser sustituido por cualquier otra implementación de Repository<Votante> sin afectar el comportamiento del sistema. |
| **LSP** | Entity | Cualquier subclase de Entity puede tratarse como Entity sin romper el programa, manteniendo el contrato del getId()/setId(). |
| **ISP** (Interface Segregation) | Repository<T> | La interfaz es pequeña y específica (5 métodos CRUD). Ningún repositorio se ve forzado a implementar métodos que no necesita. No hay métodos de "auditoría" o "reportes" mezclados en la interfaz genérica. |
| **ISP** | JuradoRepository | No implementa Repository<T> porque su modelo de datos (PK compuesta) no se ajusta al CRUD genérico, demostrando que la interfaz no fuerza implementaciones inapropiadas. |
| **DIP** (Dependency Inversion) | BiometricService | Depende de abstracciones (JsonNode, HttpClient) y no de implementaciones concretas de servicios externos. El código del servicio no sabe si FastAPI está escrito en Python, Node.js o cualquier otro lenguaje. |
| **DIP** | Controladores (Enroll, Verify, Ocr) | Dependen de servicios abstractos (BiometricService, OcrPythonService) no de implementaciones de bajo nivel. El controlador no sabe si el servicio biométrico usa HTTP, RMI o sockets. |
| **DIP** | Repositorios | Dependen de la interfaz AppConfig.getConnection() (una abstracción) y no de la implementación concreta de HikariCP directamente. |

---

## 8. Diseño Preliminar de Interfaces Gráficas (Mockups)

### Pantalla de Inicio / Identificación

```
┌─────────────────────────────────────────────────────────────────┐
│  ABIS-UPC · Sistema de Identificación Biométrica de Votación     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                                                         │   │
│   │                 [CÁMARA EN VIVO]                        │   │
│   │                 Espere la guía visual                   │   │
│   │                                                         │   │
│   │              ┌─────────────────────┐                    │   │
│   │              │  Enmarque su        │                    │   │
│   │              │  documento aquí     │                    │   │
│   │              └─────────────────────┘                    │   │
│   │                                                         │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   [📷 CAPTURAR DOCUMENTO]                                       │
│                                                                  │
│   ────────────────────────────────────────────────────────────  │
│                                                                  │
│   O escanee el código de barras del reverso                     │
│   [🔲 ESCANEAR PDF417/QR]                                       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Pantalla de Verificación Biométrica

```
┌─────────────────────────────────────────────────────────────────┐
│  ABIS-UPC · Verificación de Huella Dactilar                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                                                         │   │
│   │       👤          DATOS DEL VOTANTE                    │   │
│   │     ┌─────┐      Identificación: 1.007.819.137         │   │
│   │     │Foto │      Nombre: DANIEL DAVID                  │   │
│   │     │     │      Apellidos: TURIZO CHACON              │   │
│   │     └─────┘      Rol: ESTUDIANTE                       │   │
│   │                   Puesto: SEDE CENTRAL                 │   │
│   │                   Estado: PENDIENTE ✓                  │   │
│   │                                                         │   │
│   │              [🖐 COLOCA TU DEDO]                        │   │
│   │               ┌─────────────┐                          │   │
│   │               │  SENSOR     │                          │   │
│   │               │  AS608      │                          │   │
│   │               └─────────────┘                          │   │
│   │                                                         │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   [⏪ CANCELAR]                           Estado: Esperando...   │
└─────────────────────────────────────────────────────────────────┘
```

### Pantalla de Tarjetón (Votación)

```
┌─────────────────────────────────────────────────────────────────┐
│  ABIS-UPC · Seleccione su Candidato                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ELECCIÓN: CONSEJO ESTUDIANTIL 2026                        │   │
│   ─────────────────────────────────────────────               │   │
│                                                                  │
│   Cargo: PERSONERO/A ESTUDIANTIL                                │   │
│   ┌───────────────────────────────────────────────────────┐    │
│   │  ○ 101 · MARÍA GARCÍA        ○ 102 · PEDRO RAMÍREZ   │    │
│   │  ○ 103 · LUISA FERNÁNDEZ     ○ 104 · CARLOS MENDOZA  │    │
│   │  ○ 105 · BLANCA PÉREZ        ○ VOTO EN BLANCO       │    │
│   └───────────────────────────────────────────────────────┘    │
│                                                                  │
│   Cargo: CONSEJERO ESTUDIANTIL                                  │
│   ┌───────────────────────────────────────────────────────┐    │
│   │  □ 201 · ANA MARTÍNEZ      □ 202 · JORGE HERRERA    │    │
│   │  □ 203 · SOFÍA LÓPEZ       □ 204 · MATEO CALDERÓN   │    │
│   │  □ 205 · LAURA CUELLAR                              │    │
│   └───────────────────────────────────────────────────────┘    │
│                                                                  │
│   [✅ CONFIRMAR VOTO]          [🔄 VOLVER A INICIAR]           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Pantalla de Confirmación

```
┌─────────────────────────────────────────────────────────────────┐
│  ABIS-UPC · Voto Registrado                                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│                    ✅                                            │
│            SU VOTO HA SIDO REGISTRADO                            │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │  ELECCIÓN: CONSEJO ESTUDIANTIL 2026                     │   │
│   │  FECHA: 15/05/2026 10:32:45                            │   │
│   │  PUESTO: SEDE CENTRAL - MESA 1                         │   │
│   │                                                         │   │
│   │  Se enviará un certificado a su correo registrado       │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   [🔚 FINALIZAR]                                                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 9. Resumen del Diagrama de Clases UML

```
┌────────────────────────────────────────────────────────────────────┐
│              DIAGRAMA DE CLASES ABIS-UPC (Vista General)           │
├────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────────────────────────────────────────────┐          │
│  │ <<Java>>                                             │          │
│  │ com.abisupc                                          │          │
│  │                                                      │          │
│  │  ┌─────────────┐     ┌─────────────────────────────┐│          │
│  │  │  Entity      │     │  Repository<T> <<interface>>││          │
│  │  │  # id: Long  │◄────│  + findById()              ││          │
│  │  └──────┬──────┘     │  + findAll()                ││          │
│  │         │            │  + save()                    ││          │
│  │         │extends     │  + update()                  ││          │
│  │         │            │  + delete()                  ││          │
│  │  ┌──────┴──────┐     └──────────┬──────────────────┘│          │
│  │  │  Votante    │                │ implements        │          │
│  │  │  +identi-   │◄───────────────┘                   │          │
│  │  │  ficacion   │                ┌──────────────────┐│          │
│  │  │  +plantilla │                │VotanteRepository ││          │
│  │  │  Biometrica│                │<<Repository>>    ││          │
│  │  └─────────────┘                └──────────────────┘│          │
│  │                              ... (11 repos más)     │          │
│  │                                                      │          │
│  │  ┌─────────────────┐  ┌─────────────────────────┐   │          │
│  │  │ <<Controller>>   │  │ <<Service>>             │   │          │
│  │  │ EnrollController │─>│ BiometricService        │   │          │
│  │  │ VerifyController │─>│                         │   │          │
│  │  │ OcrController    │─>│ OcrPythonService        │   │          │
│  │  │ TestController   │  │                         │   │          │
│  │  └─────────────────┘  └──────────┬──────────────┘   │          │
│  │                                  │uses               │          │
│  │  ┌──────────────────────────────┐│                   │          │
│  │  │ <<Integration>>              ││                   │          │
│  │  │ BiometricClient              │◄┘                   │          │
│  │  │  + Unirest → FastAPI (:8001)│                    │          │
│  │  │  + Unirest → OCR (:8002)    │                    │          │
│  │  └──────────────────────────────┘                    │          │
│  │                                                      │          │
│  │  ┌──────────────┐  ┌──────────────────────────────┐ │          │
│  │  │AppConfig     │  │ AuthMiddleware               │ │          │
│  │  │+ HikariCP    │  │ + authenticate(ctx)          │ │          │
│  │  │+ Oracle XE   │  └──────────────────────────────┘ │          │
│  │  └──────┬───────┘                                   │          │
│  │         │uses                                       │          │
│  │  ┌──────┴──────────────────────────────────────┐    │          │
│  │  │ AppServer (main) · Javalin :7000            │    │          │
│  │  │ POST /enroll  POST /verify                  │    │          │
│  │  │ POST /document/scan  GET /health             │    │          │
│  │  └─────────────────────────────────────────────┘    │          │
│  └──────────────────────────────────────────────────────┘          │
└────────────────────────────────────────────────────────────────────┘
```

---

## 10. Leyenda de relaciones entre capas

| Símbolo | Significado |
|---------|-------------|
| `→` | Dependencia / Asociación |
| `◈` | Agregación (débil) |
| `◆` | Composición (fuerte) |
| `⟶` | Herencia (extends) |
| `···>` | Implementación (implements) |
| `:8001` | Puerto del microservicio biométrico |
| `:8002` | Puerto del microservicio OCR |
| `:7000` | Puerto del backend Java (Javalin) |
