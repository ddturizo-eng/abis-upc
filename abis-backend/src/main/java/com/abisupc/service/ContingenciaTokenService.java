package com.abisupc.service;

import com.abisupc.model.Eleccion;
import com.abisupc.model.TokenContingencia;
import com.abisupc.model.Votante;
import com.abisupc.repository.EleccionRepository;
import com.abisupc.repository.RegistroVotoRepository;
import com.abisupc.repository.TokenContingenciaRepository;
import com.abisupc.repository.VotanteRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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

    public ContingenciaTokenService() {
        this(new TokenContingenciaRepository(), new VotanteRepository(), new EleccionRepository(),
                new RegistroVotoRepository(), new VotacionService());
    }

    public ContingenciaTokenService(
            TokenContingenciaRepository tokenRepository,
            VotanteRepository votanteRepository,
            EleccionRepository eleccionRepository,
            RegistroVotoRepository registroVotoRepository,
            VotacionService votacionService
    ) {
        this.tokenRepository = tokenRepository;
        this.votanteRepository = votanteRepository;
        this.eleccionRepository = eleccionRepository;
        this.registroVotoRepository = registroVotoRepository;
        this.votacionService = votacionService;
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
                hint(normalizado)
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
}
