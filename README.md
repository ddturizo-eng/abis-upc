<p align="center">
  <img src="abis-backend/src/main/resources/assets/PRESENTACION GIT-HUB.png" alt="ABIS-UPC" width="800">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk" alt="Java 21">
  <img src="https://img.shields.io/badge/Python-3.11-3776AB?logo=python" alt="Python 3.11">
  <img src="https://img.shields.io/badge/Oracle-XE-red?logo=oracle" alt="Oracle XE">
  <img src="https://img.shields.io/badge/C%23-.NET%204.8-512BD4?logo=dotnet" alt="C# .NET">
  <img src="https://img.shields.io/badge/Node.js-20-339933?logo=node.js" alt="Node.js">
  <img src="https://img.shields.io/badge/license-MIT-brightgreen" alt="MIT">
</p>

---

## Indice

- [Descripcion](#descripcion)
- [Problema que resuelve](#problema-que-resuelve)
- [Arquitectura](#arquitectura)
- [Modulos y funcionalidades](#modulos-y-funcionalidades)
- [Stack tecnologico](#stack-tecnologico)
- [Requisitos](#requisitos)
- [Instalacion](#instalacion)
- [Variables de entorno](#variables-de-entorno)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Base de datos](#base-de-datos)
- [Scripts](#scripts)
- [Equipo](#equipo)
- [Licencia](#licencia)

---

## Descripcion

**ABIS-UPC** (Automated Biometric Identification System) es una plataforma de votacion electronica biometrica disenada y construida para la **Universidad Popular del Cesar**. Permite registrar votantes mediante reconocimiento OCR de documentos de identidad colombianos y verificacion por huella dactilar, gestionar jornadas electorales con anonimato garantizado del sufragio, y administrar todo el proceso desde un panel centralizado.

El sistema cumple con la **Ley 1581 de 2012** (proteccion de datos personales) y los **Acuerdos 032/1994 y 008/2016** del Consejo Superior de la UPC que rigen los procesos electorales universitarios.

**Materia:** Programacion de Computadores III (SS462) -- Ing. Alfredo Bautista  
**Universidad:** Universidad Popular del Cesar -- 2026

---

## Problema que resuelve

| Situacion anterior | Como lo resuelve ABIS-UPC |
|---|---|
| Registro manual de votantes en papel | OCR automatico de cedula colombiana + enrolamiento biometrico por huella |
| Suplantacion de identidad en mesas | Verificacion dactilar con sensor DigitalPersona o fallback por QR de contingencia |
| Conteo manual de votos con errores | Votacion electronica con registro atomico en Oracle (transaccion ACID) |
| Falta de anonimato en el sufragio | Separacion irreversible de identidad y voto en tablas distintas via stored procedure |
| Asignacion arbitraria de jurados | Motor inteligente con 7 reglas de negocio validadas por triggers PL/SQL |
| Sin trazabilidad de cambios | Auditoria automatica de cada modificacion sobre datos de votantes |
| Dificultad para notificar a votantes | Envio automatico de certificado PDF post-voto y QR de contingencia al correo |

---

## Arquitectura

```
[Frontend HTML/JS/CSS]
        |
        v
[Backend Java -- Javalin :7000]   <-- AuthMiddleware (token en SESIONES)
        |
        +---> [FastAPI Biometrico :8001]  <---> [NativeService C# :8765]
        |          |                              (lector DigitalPersona)
        |          +-- Oracle XE :1521
        |
        +---> [FastAPI OCR :8002]
        |          +-- PaddleOCR / Tesseract
        |
        +---> [Email Service Node.js :8010]
                   +-- Resend API (correos)
                   +-- Puppeteer (PDFs)
```

### Servicios y puertos

| Servicio | Puerto | Tecnologia | Descripcion |
|---|---|---|---|
| abis-backend | 7000 | Java 21 + Javalin 6 | Backend principal, logica de negocio, REST API, sirve el frontend estatico |
| abis-biometric | 8001 | Python 3.11 + FastAPI | Enrolamiento y verificacion de huella dactilar |
| abis-ocr | 8002 | Python 3.11 + FastAPI | OCR de documentos de identidad colombianos |
| abis-native | 8765 | C# + .NET 4.8 | Interfaz WebSocket con el lector DigitalPersona / AS608 |
| abis-email-service | 8010 | Node.js + Express | Generacion de PDFs (Puppeteer) y envio de correos (Resend) |
| Oracle XE | 1521 | Oracle Database XE 21c | Base de datos principal |

---

## Modulos y funcionalidades

### Registro biometrico de votantes
- Escaneo OCR de cedula colombiana (PaddleOCR / Tesseract)
- Deteccion y recorte automatico del documento (4 estrategias OpenCV en cascada)
- Captura de foto del rostro via webcam
- Enrolamiento de huella dactilar (4 muestras, plantilla cifrada AES-256)
- Flujo guiado de 4 pasos con validacion en cada etapa
- Validacion contra censo institucional (tabla POTENCIAL_VOTANTES)

### Gestion de elecciones
- CRUD completo de elecciones con fechas de inicio y cierre
- Configuracion de roles participantes por eleccion (checkboxes con peso de voto)
- Estados: PROGRAMADA, EN_CURSO, CERRADA
- Panel de elegibilidad con conteos por rol (pendientes, ejercido)
- Resultados en vivo durante la jornada
- Acta de ganadores en HTML imprimible

### Gestion de votantes
- Censo electoral con filtros (rol, puesto, estado, biometria, QR)
- Edicion de datos personales con auditoria automatica
- Inhabilitacion / habilitacion administrativa
- Re-enrolamiento biometrico con huella real via WebSocket
- Cambio de foto del rostro desde el editor

### Motor de asignacion de jurados
- Wizard progresivo de 3 pasos: Elegibles, Distribucion, Revision
- 7 reglas de negocio aplicadas (RN-01 a RN-07)
- Pool de elegibles filtrado por ELECCION_ROLES, edad >30, solo DOCENTE/ADMINISTRATIVO
- Asignacion automatica con prioridad de administrativos sobre docentes
- 6 jurados por mesa: 3 titulares (Presidente, Vicepresidente, Vocal) + 3 suplentes
- Simulacion previa con tabla de cobertura por mesa
- Trigger PL/SQL: no duplicado en misma eleccion (RN-04)
- Pantalla de resultado post-generacion con detalle por mesa

### Votacion electronica (kiosco)
- Verificacion de identidad por huella dactilar
- Fallback por escaner 2D (codigo QR de contingencia)
- Tarjeton de votacion con candidatos + voto en blanco
- Confirmacion con nombre del candidato seleccionado
- Registro atomico via stored procedure (transaccion ACID)
- **Anonimato garantizado**: VOTOS sin identificacion, REGISTRO_VOTOS sin candidato
- Envio automatico de certificado PDF al correo del votante

### Contingencia QR
- Generacion de tokens QR para votantes habilitados por eleccion
- Envio masivo de QR al correo electronico
- Filtro por ELECCION_ROLES (solo roles configurados para la eleccion)
- Panel de seguimiento con KPIs

### Certificados y notificaciones
- Envio automatico de certificado PDF post-voto (async, no bloqueante)
- Panel de seguimiento: Post-voto, Actas de ganadores, Cartas de designacion, Plantillas, Verificacion publica
- Reenvio manual de certificados fallidos
- Donut chart de distribucion por eleccion
- Sidebar de actividad reciente
- Toasts de notificacion unificados en todo el sistema (vanilla JS)

### Administracion
- Dashboard con metricas en tiempo real (votantes, biometria, participacion)
- Perfil de administrador con datos de sesion
- Navegacion por pestanas: Dashboard, Registro, Votantes, Elecciones, Candidatos, Jurados, Jornada, Contingencia, Certificados
- Auditoria automatica de cada modificacion sobre votantes (tabla AUDITORIA_VOTANTES)

### Candidatos
- CRUD de candidatos por eleccion y cargo
- Numero de campana unico por eleccion
- Vista previa del tarjeton

---

## Stack tecnologico

| Capa | Tecnologia | Version |
|---|---|---|
| Backend principal | Java + Javalin | JDK 21, Javalin 6.1.3 |
| API biometrica | Python + FastAPI | 3.11 |
| API OCR | Python + FastAPI | 3.11 |
| Lector huella | C# .NET Framework | 4.8 |
| Email / PDF | Node.js + Express + Puppeteer | 20 LTS |
| Base de datos | Oracle XE | 21c |
| Pool conexiones | HikariCP | 5.1.0 |
| Cifrado biometrico | AES-256-CBC (JCEKS KeyStore) | -- |
| Hashing passwords | SHA-256 | -- |
| Correos | Resend API | SDK 4.1.2 |
| Frontend | HTML5 + CSS3 + JavaScript (vanilla) | -- |
| Iconos | Material Symbols + Tabler Icons | -- |
| Graficos | Chart.js | 4.x |
| Construccion | Maven | 3.9+ |

---

## Requisitos

- **Java JDK 21** (o JDK 24 para generacion de KeyStore JCEKS)
- **Python 3.11** con `pip`
- **Oracle XE 21c** (PDB: `xepdb1`)
- **Node.js 20 LTS** con `npm`
- **Maven 3.9+**
- **.NET Framework 4.8** (solo para el lector DigitalPersona)
- **Git**

---

## Instalacion

### 1. Clonar y configurar variables de entorno

```bash
git clone https://github.com/ddturizo-eng/abis-upc.git
cd abis-upc
cp .env.example .env
# Editar .env con las credenciales reales de Oracle y Resend
```

### 2. Generar el KeyStore AES-256 (CMD, no PowerShell)

```cmd
"C:\Program Files\Java\jdk-24\bin\keytool.exe" -genseckey -alias abis-aes-key -keyalg AES -keysize 256 -storetype JCEKS -keystore abis-upc.jks
```

Guardar la contrasena en `.env` como `ABIS_KEYSTORE_PASSWORD`.

### 3. Base de datos Oracle

```sql
@abis-database/STARTUP.SQL
@abis-database/dml/02_seeds_potencial_votantes.sql
```

### 4. Backend Java

```bash
cd abis-backend
mvn clean package -DskipTests
java -jar target/abis-backend-1.0-SNAPSHOT.jar
```

### 5. Microservicio biometrico

```bash
cd abis-biometric
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

### 6. Microservicio OCR

```bash
cd abis-ocr
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
uvicorn api.main:app --reload --port 8002
```

### 7. Servicio de correo

```bash
cd abis-email-service
npm install
cp .env.example .env
# Configurar ABIS_RESEND_API_KEY
npm start
```

### 8. Servicio nativo (lector de huella)

Abrir `abis-native/abis-native.sln` en Visual Studio y ejecutar.

---

## Variables de entorno

| Variable | Descripcion | Default |
|---|---|---|
| `DB_URL` | JDBC URL de Oracle | `jdbc:oracle:thin:@localhost:1521:XE` |
| `DB_USER` | Usuario Oracle | `system` |
| `DB_PASSWORD` | Password Oracle | *(requerido)* |
| `BIOMETRIC_SERVICE_URL` | URL del servicio biometrico | `http://localhost:8001` |
| `OCR_SERVICE_URL` | URL del servicio OCR | `http://localhost:8002` |
| `NATIVE_SERVICE_URL` | URL del lector de huella | `ws://localhost:8765` |
| `ABIS_EMAIL_SERVICE_URL` | URL del servicio de correo | `http://localhost:8010` |
| `ABIS_EMAIL_SERVICE_TOKEN` | Token interno entre microservicios | *(configurar)* |
| `ABIS_RESEND_API_KEY` | API Key de Resend.com | *(configurar)* |
| `ABIS_RESEND_FROM_EMAIL` | Remitente de correos | `ABIS UPC <onboarding@resend.dev>` |
| `ABIS_KEYSTORE_PATH` | Ruta del archivo JCEKS | `./abis-upc.jks` |
| `ABIS_KEYSTORE_PASSWORD` | Password del KeyStore | *(configurar)* |

---

## Estructura del proyecto

```
abis-upc/
├── abis-backend/               # Java (Javalin) -- Puerto 7000
│   └── src/main/
│       ├── java/com/abisupc/
│       │   ├── config/         # AppConfig, pool HikariCP
│       │   ├── controller/     # Controladores REST
│       │   ├── dto/            # DTOs de transferencia
│       │   ├── integration/    # Clientes HTTP a microservicios
│       │   ├── model/          # Entidades del dominio
│       │   ├── repository/     # Acceso a Oracle (JDBC)
│       │   ├── security/       # CryptoService, HashingService, KeyStoreManager
│       │   ├── service/        # Logica de negocio
│       │   └── server/         # AppServer, punto de entrada
│       └── resources/          # Frontend estatico (HTML/JS/CSS)
├── abis-biometric/             # Python FastAPI -- Puerto 8001
│   └── app/
│       ├── main.py
│       ├── db/database.py
│       └── routers/            # enroll.py, verify.py
├── abis-ocr/                   # Python FastAPI -- Puerto 8002
│   └── ocr/                    # Motores, parsers, clasificacion
├── abis-native/                # C# .NET -- Puerto 8765 WebSocket
├── abis-email-service/         # Node.js Express -- Puerto 8010
│   └── src/
│       ├── controllers/        # certificado, contingencia
│       ├── services/           # email, pdf, certificado
│       ├── templates/          # Plantilla HTML del certificado PDF
│       └── routes/
├── abis-database/              # Scripts SQL Oracle
│   ├── ddl/                    # Creacion de tablas, modificaciones
│   ├── dml/                    # Datos maestros y semillas
│   └── procedures/             # Stored procedures, funciones, packages, triggers
├── docs/                       # Documentacion del proyecto
├── ABIS_UPC_cambios_segunda_entrega.md
├── funcionalides-pendientes.md
├── STARTUP.SQL
├── INICIAR_SERVICIOS.bat
└── DETENER_SERVICIOS.bat
```

---

## Base de datos

### Tablas principales

| Tabla | Descripcion |
|---|---|
| VOTANTES | Censo electoral completo |
| BIOMETRIA_VOTANTES | Plantillas biometricas cifradas (Ley 1581) |
| ELECCIONES | Procesos electorales |
| CANDIDATOS_ELECCION | Candidatos por eleccion y cargo |
| ELECCION_ROLES | Configuracion de roles y pesos por eleccion |
| VOTOS | Registro anonimo de votos (sin identificacion) |
| REGISTRO_VOTOS | Auditoria de participacion (sin candidato) |
| MESA_JURADOS | Mesas de votacion |
| JURADOS | Asignacion de jurados |
| AUDITORIA_VOTANTES | Log de cambios sobre votantes |
| AUDITORIA_CORREOS | Trazabilidad de envios de certificados |
| POTENCIAL_VOTANTES | Censo institucional de validacion |
| SESIONES | Tokens activos de administrador |

### Principio de anonimato

`VOTOS` contiene `ID_CANDIDATO` pero **NO** contiene `IDENTIFICACION`.  
`REGISTRO_VOTOS` contiene `IDENTIFICACION` pero **NO** contiene `ID_CANDIDATO`.  

Es imposible cruzar quien voto con por quien voto, incluso con acceso directo a la base de datos. El unico punto de escritura a ambas tablas es el stored procedure `prc_registrar_voto`, que ejecuta ambos INSERT en una sola transaccion ACID con ROLLBACK automatico si falla cualquiera.

---

## Scripts

```bash
# Iniciar todos los servicios
INICIAR_SERVICIOS.bat

# Detener todos los servicios
DETENER_SERVICIOS.bat

# Schema inicial de BD (ejecutar en SQL Developer)
STARTUP.SQL
```

---

## Equipo

| Integrante | Rol | GitHub |
|---|---|---|
| Daniel Turizo | Tech Lead + Full Stack | [@ddturizo-eng](https://github.com/ddturizo-eng) |
| Daniel Florez | Backend Security | [@IngDanielflorezz](https://github.com/IngDanielflorezz) |
| Mateo Calderon | Backend DAO | |
| Jorge Herrera | Backend DAO | |
| Ana Laura Cuellar | Frontend | |

---

## Licencia

MIT. Desarrollado para la Universidad Popular del Cesar -- 2026.
