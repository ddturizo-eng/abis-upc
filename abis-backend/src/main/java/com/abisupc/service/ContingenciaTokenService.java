package com.abisupc.service;

import com.abisupc.integration.ContingenciaEmailClient;
import com.abisupc.integration.QrRenderClient;
import com.abisupc.model.Eleccion;
import com.abisupc.model.TokenContingencia;
import com.abisupc.model.Votante;
import com.abisupc.repository.EleccionRepository;
import com.abisupc.repository.EnvioContingenciaRepository;
import com.abisupc.repository.RegistroVotoRepository;
import com.abisupc.repository.TokenContingenciaRepository;
import com.abisupc.repository.VotanteRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio que gestiona el ciclo de vida completo de los tokens de contingencia.
 *
 * <p>Un token de contingencia permite que un votante que no puede autenticarse
 * biometricamente ejerza su derecho al voto mediante un codigo QR de un solo uso
 * enviado a su correo electronico. El flujo completo es:
 * <ol>
 *   <li>Generar el token con {@link #generarToken(String, Long)}</li>
 *   <li>Emitir en lote con {@link #emitirLote(Long)} (envia QR por correo)</li>
 *   <li>El votante presenta el QR en el kiosco</li>
 *   <li>El kiosco valida con {@link #validarEscaneo(String, String, Long)}</li>
 *   <li>Tras votar, el token se marca como usado en {@link VotacionService}</li>
 * </ol>
 *
 * <p>El token se almacena hasheado en Oracle ({@code tokenHash}) y su valor
 * en texto plano ({@code tokenValor}) solo para permitir reenvios. El hint
 * son los ultimos 6 caracteres visibles para identificacion rapida.
 */
public class ContingenciaTokenService {

    private static final String TOKEN_PREFIX = "ABIS";
    private static final String TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_TOKEN_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();
    private final TokenContingenciaRepository tokenRepository;
    private final VotanteRepository votanteRepository;
    private final EleccionRepository eleccionRepository;
    private final RegistroVotoRepository registroVotoRepository;
    private final VotacionService votacionService;
    private final EnvioContingenciaRepository envioRepository;
    private final QrRenderClient qrRenderClient;
    private final ContingenciaEmailClient emailClient;

    /** Constructor por defecto. Crea sus propios repositorios y clientes. */
    public ContingenciaTokenService() {
        this(new TokenContingenciaRepository(), new VotanteRepository(), new EleccionRepository(),
                new RegistroVotoRepository(), new VotacionService(), new EnvioContingenciaRepository(),
                new QrRenderClient(), new ContingenciaEmailClient());
    }

    /**
     * Constructor para inyeccion de dependencias (util en pruebas).
     *
     * @param tokenRepository       repositorio de tokens de contingencia
     * @param votanteRepository     repositorio de votantes
     * @param eleccionRepository    repositorio de elecciones
     * @param registroVotoRepository repositorio de registros de voto
     * @param votacionService       servicio de votacion
     * @param envioRepository       repositorio de historial de envios
     * @param qrRenderClient        cliente para generar imagenes QR
     * @param emailClient           cliente para enviar correos de contingencia
     */
    public ContingenciaTokenService(
            TokenContingenciaRepository tokenRepository,
            VotanteRepository votanteRepository,
            EleccionRepository eleccionRepository,
            RegistroVotoRepository registroVotoRepository,
            VotacionService votacionService,
            EnvioContingenciaRepository envioRepository,
            QrRenderClient qrRenderClient,
            ContingenciaEmailClient emailClient
    ) {
        this.tokenRepository = tokenRepository;
        this.votanteRepository = votanteRepository;
        this.eleccionRepository = eleccionRepository;
        this.registroVotoRepository = registroVotoRepository;
        this.votacionService = votacionService;
        this.envioRepository = envioRepository;
        this.qrRenderClient = qrRenderClient;
        this.emailClient = emailClient;
    }

    /**
     * Genera o reemplaza el token de contingencia de un votante para una eleccion.
     *
     * <p>El token tiene el formato {@code ABIS-{idEleccion}-{16 chars aleatorios}}.
     * Si ya existia un token para ese votante y eleccion, se reemplaza.
     *
     * @param identificacion cedula del votante
     * @param idEleccion     ID de la eleccion
     * @return mapa con {@code token}, {@code token_hint}, {@code estado} y datos del votante
     * @throws IllegalArgumentException si el votante o la eleccion no existen
     */
    public Map<String, Object> generarToken(String identificacion, Long idEleccion) {
        String identificacionNormalizada = normalizarIdentificacion(identificacion);
        if (identificacionNormalizada == null || identificacionNormalizada.isBlank()) {
            throw new IllegalArgumentException("identificacion requerida");
        }
        if (idEleccion == null || idEleccion <= 0) {
            throw new IllegalArgumentException("idEleccion requerido");
        }

        Votante votante = votanteRepository.findByIdentificacion(identificacionNormalizada)
                .orElseThrow(() -> new IllegalArgumentException("Votante no encontrado"));
        eleccionRepository.findById(idEleccion)
                .orElseThrow(() -> new IllegalArgumentException("Eleccion no encontrada"));

        String tokenPlano = TOKEN_PREFIX + "-" + idEleccion + "-" + randomToken();
        String normalizado = normalizarToken(tokenPlano);
        TokenContingencia token = tokenRepository.guardarOReemplazar(
                votante.getIdentificacion(),
                idEleccion,
                sha256Hex(normalizado),
                hint(normalizado),
                normalizado
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("token", normalizado);
        response.put("token_hint", token.getTokenHint());
        response.put("identificacion", token.getIdentificacion());
        response.put("id_eleccion", token.getIdEleccion());
        response.put("estado", token.getEstado());
        return response;
    }

    /**
     * Retorna el resumen de tokens de contingencia para una eleccion.
     *
     * @param idEleccion ID de la eleccion
     * @return mapa con conteos por estado (generados, enviados, usados, fallidos)
     * @throws IllegalArgumentException si la eleccion no existe
     */
    public Map<String, Object> resumen(Long idEleccion) {
        validarIdEleccion(idEleccion);
        eleccionRepository.findById(idEleccion)
                .orElseThrow(() -> new IllegalArgumentException("Eleccion no encontrada"));
        Map<String, Object> response = new LinkedHashMap<>(envioRepository.resumen(idEleccion));
        response.put("idEleccion", idEleccion);
        return response;
    }

    /**
     * Lista los tokens de contingencia de una eleccion filtrando por estado de envio.
     *
     * @param idEleccion   ID de la eleccion
     * @param estadoEnvio  filtro de estado ({@code "ENVIADO"}, {@code "FALLIDO"}, etc.)
     * @return lista de mapas con datos de cada token y su ultimo envio
     */
    public List<Map<String, Object>> listarTokens(Long idEleccion, String estadoEnvio) {
        validarIdEleccion(idEleccion);
        return envioRepository.listarTokens(idEleccion, estadoEnvio);
    }

    /**
     * Retorna el historial de intentos de envio de tokens para una eleccion.
     *
     * @param idEleccion ID de la eleccion
     * @param limit      numero maximo de registros a retornar
     * @return lista de eventos de envio ordenados por fecha descendente
     */
    public List<Map<String, Object>> auditoria(Long idEleccion, int limit) {
        return envioRepository.historial(idEleccion, limit);
    }

    /**
     * Genera y envia tokens QR por correo a todos los votantes habilitados
     * para contingencia en una eleccion.
     *
     * <p>Si un votante ya tiene un token activo con valor recuperable, se
     * reutiliza. Solo se generan nuevos tokens para votantes sin token previo.
     * Los envios fallidos se registran en auditoria pero no detienen el lote.
     *
     * @param idEleccion ID de la eleccion
     * @return mapa con conteos de procesados, generados, enviados y fallidos
     * @throws IllegalArgumentException si la eleccion no existe
     */
    public Map<String, Object> emitirLote(Long idEleccion) {
        Eleccion eleccion = eleccionRepository.findById(idEleccion)
                .orElseThrow(() -> new IllegalArgumentException("Eleccion no encontrada"));
        List<Votante> votantes = votanteRepository.findHabilitadosParaContingencia(idEleccion);

        int generados = 0;
        int enviados = 0;
        int fallidos = 0;
        for (Votante votante : votantes) {
            TokenPlano token = obtenerOCrearToken(votante.getIdentificacion(), idEleccion);
            if (token.nuevo()) {
                generados++;
            }
            if (token.nuevo()) generados++;
            try {
                enviarToken(votante, eleccion, token.token(), token.entity());
                enviados++;
            } catch (Exception e) {
                fallidos++;
                envioRepository.registrar(token.entity().getIdToken(), votante.getIdentificacion(), idEleccion,
                        votante.getCorreo(), "FALLIDO", null, e.getMessage());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("idEleccion", idEleccion);
        response.put("procesados", votantes.size());
        response.put("generados", generados);
        response.put("enviados", enviados);
        response.put("fallidos", fallidos);
        return response;
    }

    /**
     * Reenvía el QR de un token existente al correo del votante.
     *
     * <p>El token debe tener un valor en texto plano recuperable. Si el valor
     * se perdio, se debe regenerar el token antes de reenviar.
     *
     * @param idToken ID del token a reenviar
     * @return mapa con {@code success} y {@code idToken}
     * @throws IllegalArgumentException si el token no existe
     * @throws IllegalStateException    si el token no tiene valor recuperable
     *                                  o si el envio falla
     */
    public Map<String, Object> reenviar(Long idToken) {
        TokenContingencia token = tokenRepository.findById(idToken)
                .orElseThrow(() -> new IllegalArgumentException("Token no encontrado"));
        String tokenValor = token.getTokenValor();
        if (tokenValor == null || tokenValor.isBlank()) {
            throw new IllegalStateException("Token sin valor recuperable; regenere el QR antes de reenviar");
        }
        Votante votante = votanteRepository.findByIdentificacion(token.getIdentificacion())
                .orElseThrow(() -> new IllegalArgumentException("Votante no encontrado"));
        Eleccion eleccion = eleccionRepository.findById(token.getIdEleccion())
                .orElseThrow(() -> new IllegalArgumentException("Eleccion no encontrada"));
        try {
            enviarToken(votante, eleccion, tokenValor, token);
            return Map.of("success", true, "message", "QR reenviado", "idToken", idToken);
        } catch (IOException e) {
            envioRepository.registrar(token.getIdToken(), votante.getIdentificacion(), token.getIdEleccion(),
                    votante.getCorreo(), "FALLIDO", null, e.getMessage());
            throw new IllegalStateException("No fue posible reenviar QR: " + e.getMessage(), e);
        }
    }

    /**
     * Revoca un token de contingencia impidiendo su uso futuro.
     *
     * @param idToken ID del token a revocar
     * @return mapa con {@code success} y {@code idToken}
     */
    public Map<String, Object> revocar(Long idToken) {
        tokenRepository.revocar(idToken);
        return Map.of("success", true, "message", "Token revocado", "idToken", idToken);
    }

    /**
     * Regenera el valor del token manteniendo la misma identidad (mismo ID).
     *
     * <p>Util cuando el QR anterior se perdio o expiro y el votante necesita
     * un nuevo codigo sin perder el historial del token original.
     *
     * @param idToken ID del token a regenerar
     * @return mapa con el nuevo {@code token_hint} y {@code estado}
     * @throws IllegalArgumentException si el token no existe
     */
    public Map<String, Object> regenerar(Long idToken) {
        TokenContingencia actual = tokenRepository.findById(idToken)
                .orElseThrow(() -> new IllegalArgumentException("Token no encontrado"));
        String tokenPlano = TOKEN_PREFIX + "-" + actual.getIdEleccion() + "-" + randomToken();
        String normalizado = normalizarToken(tokenPlano);
        TokenContingencia token = tokenRepository.guardarOReemplazar(
                actual.getIdentificacion(),
                actual.getIdEleccion(),
                sha256Hex(normalizado),
                hint(normalizado),
                normalizado
        );
        return Map.of(
                "success", true,
                "idToken", token.getIdToken(),
                "token_hint", token.getTokenHint(),
                "estado", token.getEstado()
        );
    }

    /**
     * Valida un token escaneado en el kiosco de contingencia.
     *
     * <p>Verifica formato, existencia en Oracle, estado activo, expiracion,
     * correspondencia con la eleccion activa y que el votante no haya votado ya.
     * Si todo es valido, retorna los datos del votante para confirmacion visual
     * por parte del jurado antes de proceder a registrar el voto.
     *
     * @param rawToken  valor del QR escaneado (puede tener caracteres extraños)
     * @param scannerId identificador del dispositivo scanner
     * @param idPuesto  ID del puesto donde se escaneo
     * @return mapa con {@code type} (SCAN_OK o codigo de rechazo), datos del votante
     *         y permiso de voto si el escaneo fue exitoso
     */
    public Map<String, Object> validarEscaneo(String rawToken, String scannerId, Long idPuesto) {
        String tokenNormalizado = normalizarToken(rawToken);
        if (!tokenNormalizado.matches("^ABIS-[0-9]+-[A-Z0-9]{12,32}$")) {
            return rechazo("SCAN_REJECTED", "Formato de token no valido", scannerId);
        }

        Optional<TokenContingencia> encontrado = tokenRepository.findByHash(sha256Hex(tokenNormalizado));
        if (encontrado.isEmpty()) {
            return rechazo("NOT_FOUND", "Token de contingencia no registrado", scannerId);
        }

        TokenContingencia token = encontrado.get();
        if (!"ACTIVO".equalsIgnoreCase(token.getEstado())) {
            return rechazo("SCAN_REJECTED", "Token no activo", scannerId);
        }
        if (token.getFechaExpiracion() != null && token.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            return rechazo("SCAN_REJECTED", "Token expirado", scannerId);
        }

        Eleccion activa = eleccionRepository.findActiva()
                .orElseThrow(() -> new IllegalStateException("No hay eleccion en curso"));
        if (!activa.getId().equals(token.getIdEleccion())) {
            return rechazo("SCAN_REJECTED", "Token no corresponde a la eleccion activa", scannerId);
        }

        Votante votante = votanteRepository.findByIdentificacion(token.getIdentificacion())
                .orElseThrow(() -> new IllegalStateException("Votante no encontrado"));
        boolean yaVoto = registroVotoRepository.yaVoto(votante.getIdentificacion(), token.getIdEleccion());
        if (yaVoto) {
            Map<String, Object> response = rechazo("ALREADY_VOTED", "Votante ya registro voto en esta eleccion", scannerId);
            response.put("votante", datosVotante(votante));
            return response;
        }

        Map<String, String> permiso;
        try {
            permiso = votacionService.votantePuedeVotar(votante.getIdentificacion(), token.getIdEleccion());
        } catch (Exception e) {
            return rechazo("SCAN_REJECTED", "No fue posible validar permiso de voto", scannerId);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "SCAN_OK");
        response.put("success", true);
        response.put("requiere_confirmacion_visual", true);
        response.put("scanner_id", scannerId);
        response.put("id_puesto", idPuesto);
        response.put("id_eleccion", token.getIdEleccion());
        response.put("token_hint", token.getTokenHint());
        response.put("ya_voto", false);
        response.put("permiso", permiso);
        response.put("votante", datosVotante(votante));
        return response;
    }

    /**
     * Marca un token como usado despues de que el votante registro su voto.
     *
     * @param identificacion cedula del votante
     * @param idEleccion     ID de la eleccion
     * @param idPuesto       ID del puesto donde se uso
     * @param scannerId      identificador del dispositivo
     */
    public void marcarUsado(String identificacion, Long idEleccion, Long idPuesto, String scannerId) {
        tokenRepository.marcarUsado(identificacion, idEleccion, idPuesto, scannerId);
    }

    public String normalizarToken(String value) {
        if (value == null) {
            return "";
        }
    /**
     * Normaliza un token eliminando caracteres no imprimibles y convirtiendo a mayusculas.
     *
     * @param value valor raw del token (puede venir del QR con caracteres extraños)
     * @return token normalizado listo para comparacion o almacenamiento
     */
    public String normalizarToken(String value) {
        if (value == null) return "";
        return value
                .replace("\u0000", "")
                .replace("\r", "")
                .replace("\n", "")
                .replaceAll("\\s+", "")
                .trim()
                .toUpperCase();
    }

    private Map<String, Object> rechazo(String type, String message, String scannerId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", type);
        response.put("success", false);
        response.put("message", message);
        response.put("scanner_id", scannerId);
        return response;
    }

    private TokenPlano obtenerOCrearToken(String identificacion, Long idEleccion) {
        Optional<TokenContingencia> existente = tokenRepository.findByIdentificacionEleccion(identificacion, idEleccion);
        if (existente.isPresent() && "ACTIVO".equalsIgnoreCase(existente.get().getEstado())
                && existente.get().getTokenValor() != null && !existente.get().getTokenValor().isBlank()) {
            return new TokenPlano(existente.get(), existente.get().getTokenValor(), false);
        }
        String tokenPlano = TOKEN_PREFIX + "-" + idEleccion + "-" + randomToken();
        String normalizado = normalizarToken(tokenPlano);
        TokenContingencia token = tokenRepository.guardarOReemplazar(
                identificacion,
                idEleccion,
                sha256Hex(normalizado),
                hint(normalizado),
                normalizado
        );
        return new TokenPlano(token, normalizado, true);
    }

    private void enviarToken(Votante votante, Eleccion eleccion, String tokenPlano, TokenContingencia token) throws IOException {
                identificacion, idEleccion, sha256Hex(normalizado), hint(normalizado), normalizado);
        return new TokenPlano(token, normalizado, true);
    }

    private void enviarToken(Votante votante, Eleccion eleccion, String tokenPlano,
                             TokenContingencia token) throws IOException {
        byte[] qrPng = qrRenderClient.render(tokenPlano);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identificacion", votante.getIdentificacion());
        payload.put("nombre", nombreCompleto(votante));
        payload.put("correo", votante.getCorreo());
        payload.put("idEleccion", eleccion.getId());
        payload.put("nombreEleccion", eleccion.getNombre());
        payload.put("tokenHint", token.getTokenHint());
        payload.put("qrPngBase64", Base64.getEncoder().encodeToString(qrPng));
        Map<String, Object> result = emailClient.enviarQr(payload);
        envioRepository.registrar(token.getIdToken(), votante.getIdentificacion(), eleccion.getId(),
                votante.getCorreo(), "ENVIADO", String.valueOf(result.get("messageId")), null);
    }

    private String nombreCompleto(Votante votante) {
        return String.join(" ",
                safe(votante.getPrimerNombre()),
                safe(votante.getSegundoNombre()),
                safe(votante.getPrimerApellido()),
                safe(votante.getSegundoApellido())
                safe(votante.getPrimerNombre()), safe(votante.getSegundoNombre()),
                safe(votante.getPrimerApellido()), safe(votante.getSegundoApellido())
        ).replaceAll("\\s+", " ").trim();
    }

    private void validarIdEleccion(Long idEleccion) {
        if (idEleccion == null || idEleccion <= 0) {
            throw new IllegalArgumentException("idEleccion requerido");
        }
        if (idEleccion == null || idEleccion <= 0)
            throw new IllegalArgumentException("idEleccion requerido");
    }

    private Map<String, Object> datosVotante(Votante votante) {
        String nombre = String.join(" ",
                safe(votante.getPrimerNombre()),
                safe(votante.getSegundoNombre()),
                safe(votante.getPrimerApellido()),
                safe(votante.getSegundoApellido())
                safe(votante.getPrimerNombre()), safe(votante.getSegundoNombre()),
                safe(votante.getPrimerApellido()), safe(votante.getSegundoApellido())
        ).replaceAll("\\s+", " ").trim();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("identificacion", votante.getIdentificacion());
        data.put("nombre", nombre);
        data.put("estado", votante.getEstadoVoto());
        data.put("fotoUrl", votante.getFotoUrl());
        data.put("idRol", votante.getIdRol());
        data.put("idPuesto", votante.getIdPuesto());
        return data;
    }

    private String randomToken() {
        StringBuilder token = new StringBuilder(RANDOM_TOKEN_LENGTH);
        for (int i = 0; i < RANDOM_TOKEN_LENGTH; i++) {
            token.append(TOKEN_ALPHABET.charAt(secureRandom.nextInt(TOKEN_ALPHABET.length())));
        }
        for (int i = 0; i < RANDOM_TOKEN_LENGTH; i++)
            token.append(TOKEN_ALPHABET.charAt(secureRandom.nextInt(TOKEN_ALPHABET.length())));
        return token.toString();
    }

    private String normalizarIdentificacion(String value) {
        return value == null ? null : value.replaceAll("\\D", "").trim();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible calcular hash de token", e);
        }
    }

    private String hint(String token) {
        return token.substring(Math.max(0, token.length() - 6));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record TokenPlano(TokenContingencia entity, String token, boolean nuevo) {
    }
}
    /**
     * Agrupa el token de contingencia, su valor en texto plano y si fue recien generado.
     *
     * @param entity token de contingencia persistido
     * @param token  valor en texto plano del token
     * @param nuevo  {@code true} si fue generado en esta operacion
     */
    private record TokenPlano(TokenContingencia entity, String token, boolean nuevo) {}
}
