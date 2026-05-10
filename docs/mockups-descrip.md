# Mockups del Sistema ABIS-UPC — Descripción de Vistas

Este documento enumera **todas las pantallas del sistema ABIS-UPC**, organizadas por rol de usuario (Operador, Votante, Administrador), incluyendo funcionalidades que aún no están implementadas en el backend.

---

## VISTAS DEL OPERADOR (rol físico, sin login en el sistema)

El operador es un ayudante del administrador que trabaja directamente con los votantes. Su rol es físico, no necesita credenciales en el sistema. Maneja el pre-registro, enrolamiento y verificación de identidad.

---

### 1. Vista de Pre-Registro / Escaneo de Documento

**Propósito:** Permitir al operador escanear el documento de identidad del votante, verificar los datos extraídos por OCR y guardar el registro.

**Responsable:** Operador (físico)

**Funcionalidades:**
- Subir imagen del documento (PNG/JPG/PDF) o capturar con cámara web
- Visualizar guía de cámara en tiempo real con Bounding Box dinámico (detección horizontal/vertical según tipo de documento)
- Botón "Escanear Documento" → envía imagen al endpoint `/api/ocr/scan`
- Mostrar resultado del OCR: tipo de documento detectado (CC Amarilla, CC Digital, TI, Carnet UPC)
- Rellenar formulario automáticamente con datos extraídos por OCR:
  - Tipo de documento
  - Número de identificación
  - Primer nombre, segundo nombre
  - Primer apellido, segundo apellido
  - Fecha de nacimiento
  - Sexo
  - Lugar de nacimiento
- El operador **verifica y corrige** los datos antes de guardar
- Guardar votante en tabla VOTANTES con estado PENDIENTE
- Si el votante ya existe, mostrar mensaje y opciones

**Endpoint backend:** `POST /api/registro/preregistro` (por implementar)

**Estado actual:** Frontend existe (`pages/auth/index.html`), endpoint NO implementado.

**Mockup sugerido:**
```
┌─────────────────────────────────────────────────────────────────┐
│  ABIS-UPC · PRE-REGISTRO DE VOTANTE   [Operador: Juan Pérez]    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                 [CÁMARA EN VIVO]                        │   │
│   │              ┌─────────────────────┐                    │   │
│   │              │  Enmarque el        │                    │   │
│   │              │  documento aquí     │                    │   │
│   │              └─────────────────────┘                    │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   [📷 Capturar]  [📂 Subir archivo]                             │
│                                                                  │
│   ────────────────────────────────────────────────────────────  │
│                                                                  │
│   DATOS EXTRAÍDOS POR OCR:                                       │
│   ┌────────────────────────────────────────────────────────┐    │
│   │ Tipo: CC Digital  │  #: 1.007.819.137                  │    │
│   │ Nombres: DANIEL DAVID   │  Apellidos: TURIZO CHACON    │    │
│   │ F. Nac.: 15/03/2000    │  Sexo: M                     │    │
│   │                                                     │    │
│   │ [✏️ EDITAR]  [✅ CONFIRMAR PRE-REGISTRO]              │    │
│   └────────────────────────────────────────────────────────┘    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

### 2. Vista de Enrolamiento Biométrico

**Propósito:** El operador verifica los datos del votante, captura su huella dactilar en el lector AS608 y toma la foto para el registro.

**Responsable:** Operador (físico)

**Funcionalidades:**
- Mostrar datos del votante (identificación, nombre completo, foto) para que el operador verifique
- Botón "Iniciar enrolamiento"
- El sistema se comunica con el NativeService C# → sensor AS608
- Capturar **4 muestras** de la misma huella (el votante coloca y retira el dedo 4 veces)
- Generar template biométrico a partir de las muestras
- Cifrar template con AES-256 antes de almacenar
- Calcular hash SHA-256 para verificar integridad futura
- Tomar foto del votante (cámara web) para la cedula electoral
- Guardar template y foto en Oracle (actualizar campos `plantillaBiometrica`, `hashIntegridadBiometrica`, `fotoUrl`)
- Feedback visual del progreso: "Muestra 1/4 capturada ✅" ...
- Mensaje final: "Huella enrolada exitosamente"

**Endpoints backend:**
- `POST /api/enroll` (implementado en Java)
- `/enroll/` en FastAPI (implementado en Python)

**Estado actual:** Endpoints implementados, frontend NO.

**Mockup sugerido:**
```
┌─────────────────────────────────────────────────────────────────┐
│  ABIS-UPC · ENROLAMIENTO BIOMÉTRICO                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │       DATOS DEL VOTANTE (verificar por operador)        │   │
│   │     ┌─────┐  Identificación: 1.007.819.137              │   │
│   │     │Foto │  Nombre: DANIEL DAVID TURIZO CHACON         │   │
│   │     └─────┘  Rol: ESTUDIANTE                            │   │
│   │               Puesto: SEDE CENTRAL                       │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   CAPTURA DE HUELLA:                                             │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                                                         │   │
│   │                    [SENSOR AS608]                       │   │
│   │              Coloque el dedo índice                     │   │
│   │                                                         │   │
│   │   Progreso: ■■■□□□□□  2/4 muestras capturadas          │   │
│   │                                                         │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   CAPTURA DE FOTO:                                               │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                  [CÁMARA EN VIVO]                       │   │
│   │              [📷 TOMAR FOTO]                             │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   [💾 GUARDAR ENROLAMIENTO]                                     │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

### 3. Vista de Verificación de Identidad (día de votación)

**Propósito:** El votante coloca su huella, aparecen sus datos en pantalla y el operador verifica físicamente que la persona presente coincide con la foto del sistema.

**Responsable:** Operador (físico) — confirma la identidad después del matching biométrico

**Funcionalidades:**
- El votante coloca su dedo en el sensor AS608
- El sistema compara la huella contra el template almacenado en Oracle
- Si hay **MATCH biométrico** → mostrar datos del votante:
  - Foto (capturada en enrolamiento)
  - Nombre completo
  - Identificación
  - Rol (Estudiante, Docente, Egresado, Administrativo)
  - Puesto de votación asignado
  - Estado del voto (debe ser PENDIENTE)
- **El operador verifica que la persona física coincide con la foto mostrada**
- Botón "✅ Permitir Votar" (presionado por el operador)
- Si **NO hay MATCH** → mostrar mensaje de error, opción de reintentar
- **Fallback (NO implementado):** Si la huella falla por desgaste dactilar, usar escáner YHD-9300 para leer PDF417/QR del reverso del documento

**Endpoints backend:**
- `POST /api/verify` (implementado en Java)
- `/verify/` en FastAPI (implementado en Python)

**Estado actual:** Endpoints implementados, frontend NO.

**Mockup sugerido:**
```
┌─────────────────────────────────────────────────────────────────┐
│  ABIS-UPC · VERIFICACIÓN DE IDENTIDAD                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │    [SENSOR AS608]   Votante coloque su dedo índice      │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   ──── MATCH EXITOSO ────────────────────────────────────────   │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │     ┌─────────────┐                                     │   │
│   │     │             │  Identificación: 1.007.819.137      │   │
│   │     │   FOTO      │  Nombre: DANIEL DAVID              │   │
│   │     │             │  Apellidos: TURIZO CHACON          │   │
│   │     └─────────────┘  Rol: ESTUDIANTE                    │   │
│   │                       Puesto: SEDE CENTRAL              │   │
│   │                       Estado: PENDIENTE ✅               │   │
│   │                                                         │   │
│   │  ┌─────────────────────────────────────────────────┐   │   │
│   │  │  Operador: Verifique que la persona física      │   │   │
│   │  │  coincide con la foto mostrada                  │   │   │
│   │  └─────────────────────────────────────────────────┘   │   │
│   │                                                         │   │
│   │        [✅ PERMITIR VOTAR]     [❌ RECHAZAR]            │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## VISTAS DEL VOTANTE (sin login, interacción directa)

El votante interactúa directamente con la pantalla táctil del Kiosco. **Solo él ve estas pantallas** (anonimato garantizado). El operador se retira o gira la pantalla.

---

### 4. Vista: Colocar Huella → Ver Datos del Votante

Esta pantalla es compartida con el operador en la parte inicial, pero una vez que el operador presiona "Permitir Votar", el flujo continúa para el votante.

**Nota:** Esta vista se fusiona con la Vista 3 (Verificación). El votante coloca su huella, ve sus datos, el operador confirma, y se avanza.

---

### 5. Vista del Tarjetón de Votación

**Propósito:** **SOLO el votante ve esta pantalla** (anonimato del voto). El votante selecciona sus candidatos y emite el voto.

**Responsable:** Votante (exclusivo)

**Funcionalidades:**
- Mostrar nombre de la elección activa (ej: "Consejo Estudiantil 2026")
- **El operador se retira** o la pantalla gira al modo privado
- Agrupar candidatos por cargo:
  - Personero/a Estudiantil (1 selección)
  - Consejero/a Estudiantil (puede ser múltiple según reglas)
  - Representante de Facultad (1 selección)
- Cada candidato muestra: número de campaña, nombre completo, foto (opcional)
- Opción de **"Voto en blanco"** por cargo
- Permitir al votante seleccionar un candidato por cargo
- Botón **"✅ CONFIRMAR VOTO"** → abre una alerta de confirmación
- Alerta de confirmación: "¿Está seguro de su selección?" (solo el votante la ve)

**Lógica adicional:**
- Cargar candidatos activos de la elección en curso (EstadoEleccion.EN_CURSO)
- Si la elección tiene múltiples cargos, el votante puede votar por uno de cada cargo
- Validar que el votante no haya votado ya en esta elección

**Endpoints backend (por implementar):**
- `GET /api/eleccion/activa` → retorna elección activa con candidatos
- `POST /api/voto/registrar` → registra voto atómico

**Estado actual:** NO implementado.

**Mockup sugerido:**
```
┌─────────────────────────────────────────────────────────────────┐
│  ABIS-UPC · TARJETÓN ELECTRÓNICO                                │
│  Elección: CONSEJO ESTUDIANTIL 2026                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ─── PERSONERO/A ESTUDIANTIL (seleccione 1) ────────────────    │
│                                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │  📸101   │  │  📸102   │  │  📸103   │  │  📸104   │       │
│  │ MARÍA    │  │ PEDRO    │  │ LUISA    │  │ CARLOS   │       │
│  │ GARCÍA   │  │ RAMÍREZ  │  │ FERNÁNDEZ│  │ MENDOZA  │       │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘       │
│                                                                  │
│  ┌──────────┐  ┌───────────────────────┐                       │
│  │  📸105   │  │   VOTO EN BLANCO      │                       │
│  │ BLANCA   │  │   ○                    │                       │
│  │ PÉREZ    │  └───────────────────────┘                       │
│  └──────────┘                                                   │
│                                                                  │
│  ─── CONSEJERO/A ESTUDIANTIL (seleccione hasta 3) ──────────    │
│                                                                  │
│  ☐ 201 ANA MARTÍNEZ   ☐ 202 JORGE HERRERA   ☐ 203 SOFÍA LÓPEZ  │
│  ☐ 204 MATEO CALDERÓN ☐ 205 LAURA CUELLAR                       │
│                                                                  │
│  ────────────────────────────────────────────────────────────    │
│                                                                  │
│  [✅ CONFIRMAR VOTO]                                             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

### 6. Vista de Confirmación del Voto

**Propósito:** El votante ve que su voto fue registrado exitosamente y se le informa que el certificado se enviará a su correo.

**Responsable:** Votante (exclusivo)

**Funcionalidades:**
- Mostrar mensaje de éxito: **"✓ SU VOTO HA SIDO REGISTRADO"**
- Mostrar resumen de la votación (sin candidato para preservar anonimato):
  - Nombre de la elección
  - Fecha y hora del voto
  - Puesto de votación
- **Enviar certificado automáticamente al correo electrónico** registrado del votante (campo `correo` de la tabla VOTANTES)
  - El PDF del certificado contiene: nombre, identificación, elección, fecha, puesto, código QR
- Una vez generado el certificado, el estado del votante cambia a **EJERCIDO**
- Mensaje: "Se ha enviado un certificado a su correo registrado"
- Botón "🔚 FINALIZAR" → redirige a pantalla de inicio

**Endpoint backend (por implementar):**
- `POST /api/voto/registrar` → registro atómico del voto
- `POST /api/certificado/enviar` → genera PDF y envía email (NO implementado)

**Estado actual:** NO implementado (backend ni frontend).

**Mockup sugerido:**
```
┌─────────────────────────────────────────────────────────────────┐
│  ABIS-UPC · VOTO REGISTRADO                                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│                      ✅                                           │
│                                                                  │
│              SU VOTO HA SIDO REGISTRADO                          │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │  ELECCIÓN: CONSEJO ESTUDIANTIL 2026                     │   │
│   │  FECHA: 15/05/2026 - 10:32:45                           │   │
│   │  PUESTO: SEDE CENTRAL - MESA 1                          │   │
│   │                                                         │   │
│   │  📧 Se ha enviado un certificado de votación            │   │
│   │     a su correo registrado                              │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│   [🔚 FINALIZAR]                                                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## VISTAS DEL ADMINISTRADOR (con login)

El administrador tiene acceso completo al sistema mediante autenticación. Gestiona elecciones, candidatos, puestos, jurados, votantes y reportes.

---

### 7. Vista de Login / Autenticación de Administrador

**Propósito:** Permitir al administrador iniciar sesión en el panel de control.

**Funcionalidades:**
- Campo para usuario
- Campo para contraseña (ocultar caracteres)
- Botón "Iniciar sesión"
- Validar credenciales contra tabla ADMINISTRADORES
- Crear token de sesión en tabla SESIONES (UUID)
- Redireccionar al dashboard
- Mensaje de error si credenciales inválidas
- Botón "Cerrar sesión" en el dashboard para invalidar token

**Endpoints backend (por implementar):**
- `POST /api/auth/login`
- `POST /api/auth/logout`

**Estado actual:** NO implementado (AuthMiddleware existe pero es placeholder).

**Mockup sugerido:**
```
┌─────────────────────────────────────────────────────────────────┐
│                                                                  │
│         🗳️                                                       │
│         ABIS-UPC                                                 │
│         Panel de Administración                                  │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │  Usuario                                                  │   │
│   │  [________________________]                               │   │
│   │                                                           │   │
│   │  Contraseña                                               │   │
│   │  [________________________]                               │   │
│   │                                                           │   │
│   │  [🔐 INICIAR SESIÓN]                                      │   │
│   │                                                           │   │
│   │  ⚠️ Usuario o contraseña incorrectos                     │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│  Universidad Popular del Cesar                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

### 8. Vista de Dashboard / Panel de Control

**Propósito:** Mostrar resumen general del estado del sistema electoral.

**Funcionalidades:**
- Tarjetas de estadísticas rápidas:
  - Total votantes registrados
  - Votantes que han ejercido voto
  - Porcentaje de participación
  - Estado actual de la elección (Programada / En Curso / Cerrada)
- Elección activa destacada
- Accesos directos: Elecciones, Candidatos, Estadísticas, Votantes
- Indicadores de estado de servicios:
  - ✅ Backend Java (:7000)
  - ✅ FastAPI Biométrico (:8001)
  - ✅ FastAPI OCR (:8002)
  - ✅ NativeService C# (:8765)
  - ✅ Oracle XE

**Endpoints backend (por implementar):** Varios

**Estado actual:** NO implementado.

**Mockup sugerido:**
```
┌─────────────────────────────────────────────────────────────────┐
│  ABIS-UPC · ADMIN   [👤 Admin]  [🔒 Cerrar sesión]              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐            │
│  │  450    │  │  123    │  │  27%    │  │  EN     │            │
│  │Votantes │  │ Votaron │  │Partici- │  │ CURSO   │            │
│  │Registr. │  │         │  │pación   │  │Elección │            │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘            │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  ELECCIÓN ACTIVA: CONSEJO ESTUDIANTIL 2026              │   │
│  │  Inicio: 15/05/2026 08:00   ·   Fin: 15/05/2026 18:00 │   │
│  │  Candidatos: 10   ·   Cargos: 3   ·   Puestos: 5      │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐             │
│  │  📋 Gestionar │ │  👥 Gestionar │ │  📊 Ver       │             │
│  │  Elecciones   │ │  Candidatos  │ │  Resultados  │             │
│  └──────────────┘ └──────────────┘ └──────────────┘             │
│                                                                  │
│  ESTADO DE SERVICIOS:                                            │
│  ✅ Java :7000   ✅ Bio :8001   ✅ OCR :8002   ✅ Native :8765   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

### 9. Vista de Gestión de Elecciones

**Propósito:** Crear, editar, iniciar, cerrar y eliminar elecciones.

**Funcionalidades:**
- Listar todas las elecciones con filtro por estado
- **Crear:** nombre, fecha/hora inicio, fecha/hora fin → estado PROGRAMADA
- **Editar:** modificar fechas o nombre (solo si PROGRAMADA)
- **Iniciar:** cambiar a EN_CURSO (valida que no haya otra activa)
- **Cerrar:** cambiar a CERRADA
- **Eliminar:** solo si no tiene votos asociados

**Endpoints backend (por implementar):**
- CRUD completo sobre tabla ELECCIONES

**Estado actual:** EleccionRepository existe, endpoints NO.

---

### 10. Vista de Gestión de Candidatos

**Propósito:** Agregar, editar y eliminar candidatos por elección.

**Funcionalidades:**
- Seleccionar elección
- Listar candidatos agrupados por cargo
- **Crear:** nombres, apellidos, cargo, número de campaña
- **Editar:** modificar datos (solo si elección PROGRAMADA o EN_CURSO)
- **Eliminar:** solo si no tiene votos asociados
- Validar número de campaña único por elección

**Endpoints backend (por implementar):**
- CRUD completo sobre tabla CANDIDATOS

**Estado actual:** CandidatoRepository existe, endpoints NO.

---

### 11. Vista de Gestión de Roles

**Propósito:** Definir los tipos de votante y el peso de su voto.

**Funcionalidades:**
- Listar roles (Estudiante, Docente, Egresado, Administrativo)
- **Crear:** nombre + peso de voto (ej: Estudiante=1, Docente=2)
- **Editar:** modificar peso de voto
- **Eliminar:** solo si no hay votantes asignados

**Endpoints backend (por implementar):**
- CRUD completo sobre tabla ROLES

**Estado actual:** RolRepository existe, endpoints NO.

---

### 12. Vista de Gestión de Puestos de Votación

**Propósito:** Crear y administrar ubicaciones físicas de votación.

**Funcionalidades:**
- Listar puestos (ciudad, sede, nombre)
- **Crear:** ciudad, sede, nombre del puesto
- **Editar / Eliminar:** con control de dependencias

**Endpoints backend (por implementar):**
- CRUD completo sobre tabla PUESTOS_VOTACION

**Estado actual:** PuestoVotacionRepository existe, endpoints NO.

---

### 13. Vista de Gestión de Mesas y Jurados

**Propósito:** Asignar jurados a mesas de votación y gestionar sus turnos.

**Funcionalidades:**
- Listar mesas por puesto
- **Crear mesa:** seleccionar puesto
- **Asignar jurado:** buscar votante por identificación
- Ver jurados por mesa con horarios
- Control de hora de ingreso/salida

**Endpoints backend (por implementar):**
- CRUD sobre MESA_JURADOS y JURADOS

**Estado actual:** MesaJuradoRepository y JuradoRepository existen, endpoints NO.

---

### 14. Vista de Gestión de Votantes (Censo Electoral)

**Propósito:** Ver, editar y administrar los votantes registrados.

**Funcionalidades:**
- Listar votantes con filtros (rol, puesto, estado)
- Buscar por identificación
- **Ver detalle:** datos personales, biométricos, estado
- **Editar:** datos personales, cambio de rol/puesto
- **Inhabilitar / Habilitar:** cambiar estado
- **Anonimizar:** eliminar datos biométricos post-elección

**Endpoints backend (por implementar):**
- CRUD completo sobre tabla VOTANTES

**Estado actual:** VotanteRepository existe, endpoints NO.

---

### 15. Vista de Resultados en Tiempo Real

**Propósito:** Mostrar resultados electorales en tiempo real.

**Funcionalidades:**
- Seleccionar elección
- Resultados simples (conteo) y ponderados (por peso de voto)
- Gráficos de barras y torta
- Totales de participación

**Endpoints backend (por implementar):**
- `GET /api/resultados` y `GET /api/resultados/ponderados`

**Estado actual:** VotoRepository tiene los métodos SQL, endpoints NO.

---

### 16. Vista de Auditoría y Trazabilidad

**Propósito:** Proporcionar un registro forense de la votación.

**Funcionalidades:**
- Historial de votos por votante (tabla REGISTRO_VOTOS)
- Logs de sesiones de administradores
- Filtros por fecha, votante, elección, puesto
- Exportar reporte

**Endpoints backend (por implementar):**
- `GET /api/auditoria/*`

**Estado actual:** Repositorios existen, endpoints NO.

---

### 17. Vista de Generación de Certificados

**Propósito:** Generar y enviar certificados de votación.

**Funcionalidades:**
- Buscar votante por identificación
- Generar PDF con: nombre, identificación, elección, fecha, puesto, código QR
- Enviar automáticamente al correo del votante
- Descargar PDF manualmente

**Endpoints backend (por implementar):**
- `GET /api/certificado/{identificacion}` → generar PDF
- `POST /api/certificado/enviar` → generar y enviar por email

**Estado actual:** NO implementado.

---

### 18. Vista de Configuración del Sistema

**Propósito:** Configurar parámetros del sistema.

**Funcionalidades:**
- Ver URL de servicios: BD, Biométrico, OCR, Native
- Versión del sistema
- (Opcional) Respaldar / restaurar BD

**Estado actual:** NO implementado.

---

## RESUMEN DE ESTADO DE IMPLEMENTACIÓN

| # | Vista | Rol | Estado Backend | Estado Frontend |
|---|-------|-----|----------------|-----------------|
| 1 | Pre-Registro / Escaneo | Operador | Endpoint NO | ✅ Existe |
| 2 | Enrolamiento Biométrico | Operador | ✅ Implementado | ❌ NO |
| 3 | Verificación de Identidad | Operador/Votante | ✅ Implementado | ❌ NO |
| 4 | Tarjetón de Votación | Votante (exclusivo) | ❌ NO | ❌ NO |
| 5 | Confirmación + Certificado | Votante | ❌ NO (email) | ❌ NO |
| 6 | Login Administrador | Admin | ❌ NO | ❌ NO |
| 7 | Dashboard | Admin | ❌ NO | ❌ NO |
| 8 | Gestión Elecciones | Admin | Repositorio existe | ❌ NO |
| 9 | Gestión Candidatos | Admin | Repositorio existe | ❌ NO |
| 10 | Gestión Roles | Admin | Repositorio existe | ❌ NO |
| 11 | Gestión Puestos | Admin | Repositorio existe | ❌ NO |
| 12 | Gestión Mesas/Jurados | Admin | Repositorios existen | ❌ NO |
| 13 | Gestión Votantes | Admin | Repositorio existe | ❌ NO |
| 14 | Resultados Tiempo Real | Admin | Métodos en Repo | ❌ NO |
| 15 | Auditoría y Trazabilidad | Admin | Repositorios existen | ❌ NO |
| 16 | Certificados | Admin | ❌ NO | ❌ NO |
| 17 | Configuración | Admin | ❌ NO | ❌ NO |

---

## NOTAS ADICIONALES

1. **Fallback QR/PDF417:** Funcionalidad documentada con el escáner YHD-9300 para cuando el sensor biométrico falle por desgaste dactilar. NO implementada.

2. **Envío de certificados por email:** El campo `correo` existe en la tabla VOTANTES y en el modelo Java. El servicio de email NO está implementado.

3. **Modo Offline:** No contemplado actualmente.

4. **Reconocimiento facial:** Mencionado como mejora futura en la documentación.

5. **Las vistas 3 y 4 están separadas por privacidad:** La vista 3 es compartida (operador verifica identidad), la vista 4 es exclusiva del votante (tarjetón anónimo).

6. **Ciclo completo del votante:** Pre-registro (operador) → Enrolamiento (operador) → Día de elección: Verificación (operador+votante) → Tarjetón (votante solo) → Confirmación (votante) → Certificado al correo (automático).
