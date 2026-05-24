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

    public ContingenciaTokenService() {
        this(new TokenContingenciaRepository(), new VotanteRepository(), new EleccionRepository(),
                new RegistroVotoRepository(), new VotacionService(), new EnvioContingenciaRepository(),
                new QrRenderClient(), new ContingenciaEmailClient());
    }

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

    public Map<String, Object> resumen(Long idEleccion) {
        validarIdEleccion(idEleccion);
        eleccionRepository.findById(idEleccion)
                .orElseThrow(() -> new IllegalArgumentException("Eleccion no encontrada"));
        Map<String, Object> response = new LinkedHashMap<>(envioRepository.resumen(idEleccion));
        response.put("idEleccion", idEleccion);
        return response;
    }

    public List<Map<String, Object>> listarTokens(Long idEleccion, String estadoEnvio) {
        validarIdEleccion(idEleccion);
        return envioRepository.listarTokens(idEleccion, estadoEnvio);
    }

    public List<Map<String, Object>> auditoria(Long idEleccion, int limit) {
        return envioRepository.historial(idEleccion, limit);
    }

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

    public Map<String, Object> revocar(Long idToken) {
        tokenRepository.revocar(idToken);
        return Map.of("success", true, "message", "Token revocado", "idToken", idToken);
    }

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

    public void marcarUsado(String identificacion, Long idEleccion, Long idPuesto, String scannerId) {
        tokenRepository.marcarUsado(identificacion, idEleccion, idPuesto, scannerId);
    }

    public String normalizarToken(String value) {
        if (value == null) {
            return "";
        }
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
        ).replaceAll("\\s+", " ").trim();
    }

    private void validarIdEleccion(Long idEleccion) {
        if (idEleccion == null || idEleccion <= 0) {
            throw new IllegalArgumentException("idEleccion requerido");
        }
    }

    private Map<String, Object> datosVotante(Votante votante) {
        String nombre = String.join(" ",
                safe(votante.getPrimerNombre()),
                safe(votante.getSegundoNombre()),
                safe(votante.getPrimerApellido()),
                safe(votante.getSegundoApellido())
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
