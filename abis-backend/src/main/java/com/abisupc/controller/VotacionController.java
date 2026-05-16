package com.abisupc.controller;

import com.abisupc.config.AppConfig;
import com.abisupc.service.VotacionService;
import com.abisupc.util.OracleErrorHandler;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VotacionController {

    private static final VotacionService votacionService = new VotacionService();

    public static void activa(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            Map<String, Object> eleccion = eleccionActiva(conn);
            if (eleccion == null) {
                ctx.status(404).json(Map.of("error", "No hay eleccion en curso"));
                return;
            }
            Long idEleccion = ((Number) eleccion.get("id")).longValue();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("eleccion", eleccion);
            data.put("cargos", candidatosPorCargo(conn, idEleccion));
            ctx.json(data);
        } catch (Exception e) {
            System.err.println("[VotacionController] activa: " + e.getMessage());
            ctx.status(500).json(Map.of("error", "No fue posible cargar el tarjeton"));
        }
    }

    public static void registrar(Context ctx) {
        Map<?, ?> body = ctx.bodyAsClass(Map.class);
        String identificacion = text(body.get("identificacion"));

        if (identificacion == null || identificacion.isBlank()) {
            ctx.status(400).json(Map.of("error", "Datos de voto incompletos"));
            return;
        }

        try (Connection conn = AppConfig.getConnection()) {
            Map<String, Object> eleccion = eleccionActiva(conn);
            Long idEleccion = number(body.get("idEleccion"));
            if (idEleccion == null && eleccion != null) {
                idEleccion = ((Number) eleccion.get("id")).longValue();
            }

            Map<String, Object> votante = votante(conn, identificacion);
            Long idPuesto = number(body.get("idPuesto"));
            if (idPuesto == null && votante != null) {
                idPuesto = ((Number) votante.get("idPuesto")).longValue();
            }

            Long idCandidato = idCandidato(body);
            votacionService.registrarVoto(identificacion, idEleccion, idCandidato, idPuesto);
            ctx.status(201).json(Map.of("success", true, "message", "Voto registrado"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            ctx.status(403).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            if (handleOracle(ctx, e)) {
                return;
            }
            System.err.println("[VotacionController] registrar: " + e.getMessage());
            ctx.status(500).json(Map.of("error", "No fue posible registrar el voto"));
        }
    }

    public static void puedeVotar(Context ctx) {
        String identificacion = ctx.pathParam("id");
        Long idEleccion = number(ctx.queryParam("idEleccion"));
        try {
            ctx.json(votacionService.votantePuedeVotar(identificacion, idEleccion));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            if (handleOracle(ctx, e)) {
                return;
            }
            ctx.status(500).json(Map.of("error", "No fue posible validar si el votante puede votar"));
        }
    }

    public static void votante(Context ctx) {
        String identificacion = ctx.queryParam("identificacion");
        try (Connection conn = AppConfig.getConnection()) {
            Map<String, Object> eleccion = eleccionActiva(conn);
            if (eleccion == null) {
                ctx.status(404).json(Map.of("error", "No hay eleccion en curso"));
                return;
            }
            Map<String, Object> votante = votante(conn, identificacion);
            if (votante == null) {
                ctx.status(404).json(Map.of("error", "Votante no encontrado"));
                return;
            }
            Long idEleccion = ((Number) eleccion.get("id")).longValue();
            votante.put("yaVoto", yaVoto(conn, identificacion, idEleccion));
            ctx.json(votante);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "No fue posible validar el votante"));
        }
    }

    private static Map<String, Object> eleccionActiva(Connection conn) throws SQLException {
        String sql = "SELECT ID_ELECCION, NOMBRE FROM Elecciones WHERE ESTADO = 'EN_CURSO'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", rs.getLong("ID_ELECCION"));
            data.put("nombre", rs.getString("NOMBRE"));
            return data;
        }
    }

    private static List<Map<String, Object>> candidatosPorCargo(Connection conn, Long idEleccion) throws SQLException {
        String sql = "SELECT ce.ID_CANDIDATO, ce.NUMERO_CAMPANIA, ce.CARGO, c.PRIMER_NOMBRE, c.SEGUNDO_NOMBRE, " +
                "c.PRIMER_APELLIDO, c.SEGUNDO_APELLIDO FROM Candidatos_eleccion ce " +
                "JOIN Candidatos c ON c.ID_CANDIDATO = ce.ID_CANDIDATO " +
                "WHERE ce.ID_ELECCION = ? ORDER BY ce.CARGO, ce.NUMERO_CAMPANIA";
        Map<String, List<Map<String, Object>>> grupos = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cargo = rs.getString("CARGO");
                    Map<String, Object> candidato = new LinkedHashMap<>();
                    candidato.put("idCandidato", rs.getLong("ID_CANDIDATO"));
                    candidato.put("numeroCampania", rs.getInt("NUMERO_CAMPANIA"));
                    candidato.put("nombre", nombre(rs));
                    grupos.computeIfAbsent(cargo, k -> new ArrayList<>()).add(candidato);
                }
            }
        }
        List<Map<String, Object>> cargos = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grupos.entrySet()) {
            cargos.add(Map.of("cargo", entry.getKey(), "candidatos", entry.getValue()));
        }
        return cargos;
    }

    private static Map<String, Object> votante(Connection conn, String identificacion) throws SQLException {
        String sql = "SELECT IDENTIFICACION, PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, " +
                "ESTADO_VOTO, FOTO_URL, ID_ROL, ID_PUESTO FROM Votantes WHERE IDENTIFICACION = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("identificacion", rs.getString("IDENTIFICACION"));
                data.put("nombre", nombre(rs));
                data.put("estado", rs.getString("ESTADO_VOTO"));
                data.put("fotoUrl", rs.getString("FOTO_URL"));
                data.put("idRol", rs.getLong("ID_ROL"));
                data.put("idPuesto", rs.getLong("ID_PUESTO"));
                return data;
            }
        }
    }

    private static boolean yaVoto(Connection conn, String identificacion, Long idEleccion) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Registro_votos WHERE IDENTIFICACION = ? AND ID_ELECCION = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ps.setLong(2, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static String nombre(ResultSet rs) throws SQLException {
        return List.of(
                safe(rs.getString("PRIMER_NOMBRE")),
                safe(rs.getString("SEGUNDO_NOMBRE")),
                safe(rs.getString("PRIMER_APELLIDO")),
                safe(rs.getString("SEGUNDO_APELLIDO"))
        ).stream().filter(s -> !s.isBlank()).reduce((a, b) -> a + " " + b).orElse("--");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static Long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static Long idCandidato(Map<?, ?> body) {
        if (body.containsKey("idCandidato")) {
            return number(body.get("idCandidato"));
        }
        List<?> selecciones = body.get("selecciones") instanceof List<?> ? (List<?>) body.get("selecciones") : List.of();
        if (selecciones.isEmpty()) {
            return null;
        }
        if (selecciones.size() > 1) {
            throw new IllegalArgumentException("Solo se permite una seleccion por registro de voto");
        }
        Object seleccion = selecciones.get(0);
        if (!(seleccion instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Seleccion invalida");
        }
        return number(map.get("idCandidato"));
    }

    private static boolean handleOracle(Context ctx, Throwable e) {
        return OracleErrorHandler.from(e).map(error -> {
            ctx.status(error.statusCode()).json(Map.of("error", error.message(), "oraCode", error.oraCode()));
            return true;
        }).orElse(false);
    }
}
