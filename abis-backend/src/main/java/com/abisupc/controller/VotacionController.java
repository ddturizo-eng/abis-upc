package com.abisupc.controller;

import com.abisupc.config.AppConfig;
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
        List<?> selecciones = body.get("selecciones") instanceof List<?> ? (List<?>) body.get("selecciones") : List.of();

        if (identificacion == null || identificacion.isBlank() || selecciones.isEmpty()) {
            ctx.status(400).json(Map.of("error", "Datos de voto incompletos"));
            return;
        }

        try (Connection conn = AppConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> eleccion = eleccionActiva(conn);
                if (eleccion == null) {
                    throw new IllegalStateException("No hay eleccion en curso");
                }
                Long idEleccion = ((Number) eleccion.get("id")).longValue();
                Map<String, Object> votante = votante(conn, identificacion);
                if (votante == null) {
                    throw new IllegalStateException("Votante no encontrado");
                }
                String estado = String.valueOf(votante.get("estado"));
                if (!"PENDIENTE".equalsIgnoreCase(estado)) {
                    throw new IllegalStateException("Votante no habilitado");
                }
                if (yaVoto(conn, identificacion, idEleccion)) {
                    throw new IllegalStateException("Voto ya ejercido");
                }

                Long idRol = ((Number) votante.get("idRol")).longValue();
                Double peso = pesoVoto(conn, idEleccion, idRol);
                Long idPuesto = ((Number) votante.get("idPuesto")).longValue();

                for (Object item : selecciones) {
                    if (!(item instanceof Map<?, ?> seleccion)) {
                        throw new IllegalStateException("Seleccion invalida");
                    }
                    Long idCandidato = number(seleccion.get("idCandidato"));
                    if (idCandidato == null || !candidatoValido(conn, idCandidato, idEleccion)) {
                        throw new IllegalStateException("Candidato invalido");
                    }
                    insertarVoto(conn, idEleccion, idCandidato, idRol, peso);
                }

                insertarRegistro(conn, identificacion, idPuesto, idEleccion);
                actualizarEstado(conn, identificacion, "EJERCIDO");
                conn.commit();
                ctx.status(201).json(Map.of("success", true, "message", "Voto registrado"));
            } catch (Exception e) {
                conn.rollback();
                ctx.status(409).json(Map.of("error", e.getMessage()));
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            System.err.println("[VotacionController] registrar: " + e.getMessage());
            ctx.status(500).json(Map.of("error", "No fue posible registrar el voto"));
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

    private static Double pesoVoto(Connection conn, Long idEleccion, Long idRol) throws SQLException {
        String sql = "SELECT PESO_VOTO FROM Eleccion_roles WHERE ID_ELECCION = ? AND ID_ROL = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            ps.setLong(2, idRol);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("PESO_VOTO") : 1.0;
            }
        }
    }

    private static boolean candidatoValido(Connection conn, Long idCandidato, Long idEleccion) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Candidatos_eleccion WHERE ID_CANDIDATO = ? AND ID_ELECCION = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idCandidato);
            ps.setLong(2, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static void insertarVoto(Connection conn, Long idEleccion, Long idCandidato, Long idRol, Double peso) throws SQLException {
        String sql = "INSERT INTO Votos (ID_VOTO, FECHA_HORA, PESO_VOTO_APLICADO, ID_ROL, ID_ELECCION, ID_CANDIDATO) " +
                "VALUES (seq_votos.NEXTVAL, SYSTIMESTAMP, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, peso);
            ps.setLong(2, idRol);
            ps.setLong(3, idEleccion);
            ps.setLong(4, idCandidato);
            ps.executeUpdate();
        } catch (SQLException e) {
            String fallback = "INSERT INTO Votos (ID_VOTO, FECHA_HORA, PESO_VOTO_APLICADO, ID_ELECCION, ID_CANDIDATO) " +
                    "VALUES (seq_votos.NEXTVAL, SYSTIMESTAMP, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(fallback)) {
                ps.setDouble(1, peso);
                ps.setLong(2, idEleccion);
                ps.setLong(3, idCandidato);
                ps.executeUpdate();
            }
        }
    }

    private static void insertarRegistro(Connection conn, String identificacion, Long idPuesto, Long idEleccion) throws SQLException {
        String sql = "INSERT INTO Registro_votos (ID_REGISTRO, FECHA_HORA, IDENTIFICACION, ID_PUESTO, ID_ELECCION) " +
                "VALUES (seq_registro_votos.NEXTVAL, SYSTIMESTAMP, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ps.setLong(2, idPuesto);
            ps.setLong(3, idEleccion);
            ps.executeUpdate();
        }
    }

    private static void actualizarEstado(Connection conn, String identificacion, String estado) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE Votantes SET ESTADO_VOTO = ? WHERE IDENTIFICACION = ?")) {
            ps.setString(1, estado);
            ps.setString(2, identificacion);
            ps.executeUpdate();
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
}
