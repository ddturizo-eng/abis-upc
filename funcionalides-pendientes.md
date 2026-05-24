Resumen: Estado del sistema y pulidas pendientes
✅ Lo que ya funciona bien
- Login admin + tokens SESIONES
- Pre-registro con OCR + flujo de 4 pasos
- Enrolamiento biométrico completo (huella + foto + cifrado AES-256)
- Votación kiosk (pantalla welcome → verify → identity → ballot → success)
- CRUD elecciones y candidatos
- Sistema de contingencia con tokens QR
- Certificados post-voto
- Integración 4 servicios (Java ↔ FastAPI ↔ C#)
- DDL/DML/Sequences/Stored Procedures en Oracle
---
🔴 Críticas (requieren atención inmediata)
1. El stored procedure prc_registrar_voto nunca se invoca
- abis-biometric /vote solo hace UPDATE VOTANTES SET ESTADO_VOTO = 'EJERCIDO' — no inserta en VOTOS ni en REGISTRO_VOTOS
- El SP existe en la BD con la lógica correcta pero nadie lo llama
- Esto rompe el principio de anonimato del sufragio documentado
2. El votante puede votar sin que quede registro anónimo
- Si alguien vota, solo se marca su estado. No hay ballot papertrace.
3. El tarjetón no está conectado al registro de voto real
- El frontend muestra el ballot completo pero al confirmar, no se llama al SP
---
🟡 Importantés (afectan experiencia en exposición)
4. UI admin: secciones incompletas en el dashboard
- js/votantes.js, js/candidatos.js, js/jurados.js, js/jornada-admin.js — existen los JS pero hay que verificar que las secciones HTML de cada tab carguen correctamente
- Los módulos de admin cargan como contenido dinámico via admin-router.js — confirmar que todas las secciones renderizan datos reales
5. Navbar vacía (navbar.css = 1 línea)
- El CSS vive en admin-dashboard.css — inconsistentemente organizado
- No hay page 404
6. base.css bug de colores en headings
- h1, h2, h3, h4 { color: #c1ead6; } — casi invisible sobre fondo blanco
7. Fallback biométrico (contingencia 2D)
- El scanner 2D para fallback existe en qr-handler.js y scanner-handler.js
- Hay WebSocket de contingencia en votacion.js
- Pero falta verificar end-to-end que al escanear QR se muestra la foto y se permite votar
8. Voto en blanco ambiguo
- El frontend envía idCandidato = null para blanco
- El SP acepta NULL según su código, pero la FK compuesta del esquema dice lo contrario
9. Consistencia visual
- Footer login dice © 2026, votacion dice © 2025
- Modal IP Webcam usa estilos inline
- No hay favicon
---
🟢 Menores (pueden esperar o arreglarse rápido)
Item	Ubicación
Tailwind CDN (riesgo si caduca)	admin/index.html
BiometriaOracleRepository duplica BiometriaVotanteRepository	Java
cerrarEleccion duplicado en EleccionController	Java
Consola: resultadoVotoEnBlanco() busca ID_CANDIDATO IS NULL	VotacionController
---
### 📋 Plan sugerido para la expo (semana y media)
**Día 1-2: Corregir el bug crítico del SP**
- Investigar por qué `abis-biometric /vote` no llama al SP
- O сделать так, чтобы `VotacionService` en Java llame directamente al SP después de recibir la confirmación del frontend
**Día 3-4: Pulir admin dashboard**
- Verificar que cada tab (Votantes, Elecciones, Candidatos, Jurados) renderiza datos reales
- Arreglar colores de headings en `base.css`
- Probar el flujo completo: crear elección → adicionar candidatos → iniciar elección → votar
**Día 5: Testing end-to-end del flujo votación**
- Escanear votante por huella o QR
- Mostrar confirmación de identidad
- Seleccionar candidato
- Confirmar voto
- Verificar que aparece en `VOTOS` y `REGISTRO_VOTOS` en la BD
**Día 6: polish visual**
- Favicon, footer consistente, estilos inline → CSS
- Probar en el browser como si fuera un empresario
**Día 7: Buffer y rehearse**
---
¿Quieres que profundice en algún punto específico o que te dé un plan más detallado para alguno de estos items?