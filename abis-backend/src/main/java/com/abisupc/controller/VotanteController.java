package com.abisupc.controller;

import com.abisupc.model.EstadoVotante;
import com.abisupc.model.Votante;
import com.abisupc.repository.EleccionRepository;
import com.abisupc.repository.RegistroVotoRepository;
import com.abisupc.repository.VotanteRepository;
import com.abisupc.service.AdminService;
import com.abisupc.service.VotacionService;
import com.abisupc.util.OracleErrorHandler;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class VotanteController {

    private static final VotanteRepository repository = new VotanteRepository();
    private static final RegistroVotoRepository registroVotoRepository = new RegistroVotoRepository();
    private static final EleccionRepository eleccionRepository = new EleccionRepository();
    private static final AdminService adminService = new AdminService();
    private static final VotacionService votacionService = new VotacionService();

    public static void getAll(Context ctx) {
        List<Votante> votantes = repository.findAll();
        String rol = ctx.queryParam("rol");
        String estado = ctx.queryParam("estado");
        String biometrico = ctx.queryParam("biometrico");
        if (rol != null && !rol.isBlank()) {
            votantes = votantes.stream()
                    .filter(v -> rol.equalsIgnoreCase(rolNombre(v.getIdRol())))
                    .collect(Collectors.toList());
        }
        if (estado != null && !estado.isBlank()) {
            votantes = votantes.stream()
                    .filter(v -> estado.equalsIgnoreCase(v.getEstadoVoto()))
                    .collect(Collectors.toList());
        }
        if (biometrico != null && !biometrico.isBlank()) {
            boolean requerido = Boolean.parseBoolean(biometrico) || "S".equalsIgnoreCase(biometrico) || "true".equalsIgnoreCase(biometrico);
            votantes = votantes.stream()
                    .filter(v -> (v.getFechaConsentimiento() != null) == requerido)
                    .collect(Collectors.toList());
        }
        ctx.json(votantes);
    }

    public static void segundaLlave(Context ctx) {
        try {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            String qrCedula = normalizarQrCedula((String) body.get("qr_cedula"));
            String identificacion = normalizarIdentificacion((String) body.get("identificacion"));

            if (qrCedula == null || qrCedula.isBlank()) {
                ctx.status(400).json(Map.of("error", "Código no reconocido"));
                return;
            }
            if (identificacion == null || identificacion.isBlank()) {
                ctx.status(400).json(Map.of("error", "La identificación no coincide"));
                return;
            }

            Optional<Votante> encontrado = repository.findByQrCedula(qrCedula);
            if (encontrado.isEmpty()) {
                ctx.status(404).json(Map.of("error", "Código no reconocido"));
                return;
            }

            Votante votante = encontrado.get();
            if (!identificacion.equals(votante.getIdentificacion())) {
                ctx.status(409).json(Map.of("error", "La identificación no coincide"));
                return;
            }

            if (!EstadoVotante.PENDIENTE.name().equals(votante.getEstadoVoto())) {
                ctx.status(409).json(Map.of("error", "Votante no habilitado"));
                return;
            }

            Long idEleccion = eleccionRepository.findActiva().map(e -> e.getId()).orElse(null);
            boolean yaVoto = idEleccion != null && registroVotoRepository.yaVoto(identificacion, idEleccion);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requiere_confirmacion_visual", true);
            response.put("ya_voto", yaVoto);
            response.put("id_eleccion", idEleccion);
            response.put("votante", datosJornada(votante));
            ctx.json(response);
        } catch (Exception e) {
            System.err.println("[VotanteController] segundaLlave error: " + e.getMessage());
            ctx.status(500).json(Map.of("error", "Código no reconocido"));
        }
    }

    public static void puedeVotar(Context ctx) {
        try {
            String identificacion = ctx.pathParam("id");
            Long idEleccion = longValue(ctx.queryParam("idEleccion"));
            ctx.json(votacionService.votantePuedeVotar(identificacion, idEleccion));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            if (handleOracle(ctx, e)) {
                return;
            }
            ctx.status(500).json(Map.of("error", "No fue posible validar el votante"));
        }
    }

    public static void inhabilitar(Context ctx) {
        cambiarEstadoAdministrativo(ctx, true);
    }

    public static void habilitar(Context ctx) {
        cambiarEstadoAdministrativo(ctx, false);
    }

    private static String normalizarQrCedula(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("[^\\S\\n]+", " ")
                .trim();
        return normalized.substring(0, Math.min(normalized.length(), 500));
    }

    private static String normalizarIdentificacion(String value) {
        return value == null ? null : value.replaceAll("\\D", "").trim();
    }

    private static Map<String, Object> datosJornada(Votante votante) {
        Map<String, Object> data = new HashMap<>();
        data.put("identificacion", votante.getIdentificacion());
        data.put("primer_nombre", votante.getPrimerNombre());
        data.put("segundo_nombre", votante.getSegundoNombre());
        data.put("primer_apellido", votante.getPrimerApellido());
        data.put("segundo_apellido", votante.getSegundoApellido());
        data.put("estado_voto", votante.getEstadoVoto());
        data.put("foto_url", votante.getFotoUrl());
        data.put("rol_id", votante.getIdRol());
        data.put("puesto_id", votante.getIdPuesto());
        return data;
    }

    private static String rolNombre(Long idRol) {
        if (idRol == null) return "";
        if (idRol == 1L) return "ESTUDIANTE";
        if (idRol == 2L) return "DOCENTE";
        if (idRol == 3L) return "EGRESADO";
        if (idRol == 4L) return "ADMINISTRATIVO";
        return String.valueOf(idRol);
    }

    private static void cambiarEstadoAdministrativo(Context ctx, boolean inhabilitar) {
        try {
            String identificacion = ctx.pathParam("id");
            Long idAdmin = ctx.attribute("idAdmin");
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            String motivo = body.get("motivo") != null ? String.valueOf(body.get("motivo")).trim() : null;
            if (inhabilitar) {
                adminService.inhabilitarVotante(identificacion, idAdmin, motivo);
                ctx.json(Map.of("success", true, "message", "Votante inhabilitado"));
            } else {
                adminService.habilitarVotante(identificacion, idAdmin, motivo);
                ctx.json(Map.of("success", true, "message", "Votante habilitado"));
            }
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            if (handleOracle(ctx, e)) {
                return;
            }
            ctx.status(500).json(Map.of("error", "No fue posible actualizar el votante"));
        }
    }

    private static Long longValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value);
    }

    private static boolean handleOracle(Context ctx, Throwable e) {
        return OracleErrorHandler.from(e).map(error -> {
            ctx.status(error.statusCode()).json(Map.of("error", error.message(), "oraCode", error.oraCode()));
            return true;
        }).orElse(false);
    }
}
