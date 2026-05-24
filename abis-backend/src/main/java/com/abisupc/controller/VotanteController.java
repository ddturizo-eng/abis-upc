package com.abisupc.controller;

import com.abisupc.config.AppConfig;
import com.abisupc.model.EstadoVotante;
import com.abisupc.model.Votante;
import com.abisupc.repository.EleccionRepository;
import com.abisupc.repository.RegistroVotoRepository;
import com.abisupc.repository.VotanteRepository;
import com.abisupc.service.AdminService;
import com.abisupc.service.VotacionService;
import com.abisupc.util.OracleErrorHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class VotanteController {

    private static final VotanteRepository repository = new VotanteRepository();
    private static final RegistroVotoRepository registroVotoRepository = new RegistroVotoRepository();
    private static final EleccionRepository eleccionRepository = new EleccionRepository();
    private static final AdminService adminService = new AdminService();
    private static final VotacionService votacionService = new VotacionService();
    private static final ObjectMapper mapper = new ObjectMapper();

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
                    .filter(v -> v.isBiometrico() == requerido)
                    .collect(Collectors.toList());
        }
        ctx.json(votantes);
    }

    public static void porEleccion(Context ctx) {
        try {
            String idEleccionParam = ctx.queryParam("idEleccion");
            if (idEleccionParam == null || idEleccionParam.isBlank()) {
                ctx.status(400).json(Map.of("error", "idEleccion requerido"));
                return;
            }
            Long idEleccion = Long.parseLong(idEleccionParam);
            List<Votante> votantes = repository.findByEleccion(idEleccion);
            ctx.json(votantes);
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "idEleccion invalido"));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error consultando votantes por eleccion"));
        }
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

    public static void editar(Context ctx) {
        try {
            String identificacion = ctx.pathParam("id");
            Votante existente = repository.findByIdentificacion(identificacion)
                    .orElseThrow(() -> new IllegalArgumentException("Votante no encontrado"));
            String oldCorreo = existente.getCorreo();
            String oldPNombre = existente.getPrimerNombre();
            String oldSNombre = existente.getSegundoNombre();
            String oldPApellido = existente.getPrimerApellido();
            String oldSApellido = existente.getSegundoApellido();
            Long oldIdRol = existente.getIdRol();
            Long oldIdPuesto = existente.getIdPuesto();
            java.sql.Date oldFechaNac = existente.getFechaNacimiento();
            JsonNode body = mapper.readTree(ctx.body());

            String primerNombre = text(body, "primer_nombre", "primerNombre");
            String primerApellido = text(body, "primer_apellido", "primerApellido");
            if (primerNombre == null || primerNombre.isBlank()) {
                throw new IllegalArgumentException("Primer nombre requerido");
            }
            if (primerApellido == null || primerApellido.isBlank()) {
                throw new IllegalArgumentException("Primer apellido requerido");
            }

            existente.setCorreo(text(body, "correo"));
            existente.setPrimerNombre(primerNombre.trim());
            existente.setSegundoNombre(blankToNull(text(body, "segundo_nombre", "segundoNombre")));
            existente.setPrimerApellido(primerApellido.trim());
            existente.setSegundoApellido(blankToNull(text(body, "segundo_apellido", "segundoApellido")));
            Long idRol = longValue(text(body, "rol_id", "idRol"));
            Long idPuesto = longValue(text(body, "puesto_id", "idPuesto"));
            String fechaNac = text(body, "fecha_nacimiento", "fechaNacimiento");
            if (idRol != null) existente.setIdRol(idRol);
            if (idPuesto != null) existente.setIdPuesto(idPuesto);
            if (fechaNac != null && !fechaNac.isBlank()) {
                try { existente.setFechaNacimiento(java.sql.Date.valueOf(fechaNac.trim())); }
                catch (IllegalArgumentException ignored) { }
            }

            repository.update(existente);
            Long idAdmin = ctx.attribute("idAdmin");
            registrarCambio(identificacion, idAdmin, "CORREO", oldCorreo, existente.getCorreo());
            registrarCambio(identificacion, idAdmin, "PRIMER_NOMBRE", oldPNombre, existente.getPrimerNombre());
            registrarCambio(identificacion, idAdmin, "SEGUNDO_NOMBRE", oldSNombre, existente.getSegundoNombre());
            registrarCambio(identificacion, idAdmin, "PRIMER_APELLIDO", oldPApellido, existente.getPrimerApellido());
            registrarCambio(identificacion, idAdmin, "SEGUNDO_APELLIDO", oldSApellido, existente.getSegundoApellido());
            registrarCambio(identificacion, idAdmin, "ID_ROL", oldIdRol != null ? oldIdRol.toString() : null,
                    existente.getIdRol() != null ? existente.getIdRol().toString() : null);
            registrarCambio(identificacion, idAdmin, "ID_PUESTO", oldIdPuesto != null ? oldIdPuesto.toString() : null,
                    existente.getIdPuesto() != null ? existente.getIdPuesto().toString() : null);
            registrarCambio(identificacion, idAdmin, "FECHA_NACIMIENTO",
                    oldFechaNac != null ? oldFechaNac.toString() : null,
                    existente.getFechaNacimiento() != null ? existente.getFechaNacimiento().toString() : null);
            ctx.json(Map.of("success", true, "votante", existente));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            if (handleOracle(ctx, e)) {
                return;
            }
            ctx.status(500).json(Map.of("error", "No fue posible actualizar el votante"));
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

    private static String text(JsonNode body, String... fields) {
        if (body == null) {
            return null;
        }
        for (String field : fields) {
            if (body.hasNonNull(field)) {
                return body.get(field).asText();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean handleOracle(Context ctx, Throwable e) {
        return OracleErrorHandler.from(e).map(error -> {
            ctx.status(error.statusCode()).json(Map.of("error", error.message(), "oraCode", error.oraCode()));
            return true;
        }).orElse(false);
    }

    private static void registrarCambio(String identificacion, Long idAdmin, String campo, String valorAnt, String valorNuevo) {
        if (idAdmin == null || campo == null) return;
        if (Objects.equals(valorAnt, valorNuevo)) return;
        if (valorAnt == null && valorNuevo == null) return;
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO Auditoria_votantes (ID_AUDITORIA, IDENTIFICACION, ID_ADMIN, CAMPO_MODIFICADO, VALOR_ANTERIOR, VALOR_NUEVO, ACCION, FECHA_HORA) " +
                 "VALUES (seq_auditoria_votantes.NEXTVAL, ?, ?, ?, ?, ?, 'EDICION_DATOS', SYSDATE)")) {
            ps.setString(1, identificacion);
            ps.setLong(2, idAdmin);
            ps.setString(3, campo);
            ps.setString(4, truncate(valorAnt, 500));
            ps.setString(5, truncate(valorNuevo, 500));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[VotanteController] Error registrando auditoria: " + e.getMessage());
        }
    }

    private static String truncate(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(value.length(), max));
    }
}
