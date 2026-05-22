package com.abisupc.controller;

import com.abisupc.config.AppConfig;
import com.abisupc.service.EleccionLifecycleService;
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
    private static final EleccionLifecycleService lifecycleService = new EleccionLifecycleService();

    public static void activa(Context ctx) {
        lifecycleService.sincronizarEstados();
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
        lifecycleService.sincronizarEstados();
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
        lifecycleService.sincronizarEstados();
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

    public static void jornadaEstadisticas(Context ctx) {
        lifecycleService.sincronizarEstados();
        try (Connection conn = AppConfig.getConnection()) {
            Long idEleccion = number(ctx.queryParam("idEleccion"));
            Map<String, Object> eleccion = idEleccion != null ? eleccionPorId(conn, idEleccion) : eleccionActiva(conn);
            if (eleccion == null) {
                eleccion = ultimaEleccion(conn);
            }
            if (eleccion == null) {
                ctx.status(404).json(Map.of("error", "No hay elecciones para analizar"));
                return;
            }

            final Long idEleccionSeleccionada = ((Number) eleccion.get("id")).longValue();
            long totalHabilitados = count(conn, "SELECT COUNT(*) FROM Votantes WHERE UPPER(ESTADO_VOTO) IN ('PENDIENTE', 'EJERCIDO')");
            long votosEmitidos = countById(conn, "SELECT COUNT(*) FROM Votos WHERE ID_ELECCION = ?", idEleccionSeleccionada);
            long registros = countById(conn, "SELECT COUNT(*) FROM Registro_votos WHERE ID_ELECCION = ?", idEleccionSeleccionada);

            Map<String, Object> resumen = new LinkedHashMap<>();
            resumen.put("totalHabilitados", totalHabilitados);
            resumen.put("votosEmitidos", votosEmitidos);
            resumen.put("registrosVoto", registros);
            resumen.put("participacion", totalHabilitados > 0 ? Math.round((registros * 100.0) / totalHabilitados) : 0);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("eleccion", eleccion);
            data.put("resumen", resumen);
            List<String> advertencias = new ArrayList<>();
            data.put("resultadosPorCandidato", safeMetric(advertencias, "resultadosPorCandidato", () -> resultadosPorCandidato(conn, idEleccionSeleccionada)));
            data.put("resultadosPorRol", safeMetric(advertencias, "resultadosPorRol", () -> resultadosPorRol(conn, idEleccionSeleccionada)));
            data.put("participacionPorRol", safeMetric(advertencias, "participacionPorRol", () -> participacionPorRol(conn, idEleccionSeleccionada)));
            data.put("participacionPorPuesto", safeMetric(advertencias, "participacionPorPuesto", () -> participacionPorPuesto(conn, idEleccionSeleccionada)));
            data.put("participacionPorCiudad", safeMetric(advertencias, "participacionPorCiudad", () -> participacionPorCiudad(conn, idEleccionSeleccionada)));
            data.put("advertencias", advertencias);
            ctx.json(data);
        } catch (Exception e) {
            System.err.println("[VotacionController] jornadaEstadisticas: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "No fue posible cargar estadisticas de jornada", "detail", e.getMessage()));
        }
    }

    private static Map<String, Object> eleccionActiva(Connection conn) throws SQLException {
        String sql = "SELECT ID_ELECCION, NOMBRE, ESTADO FROM Elecciones WHERE ESTADO = 'EN_CURSO'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", rs.getLong("ID_ELECCION"));
            data.put("nombre", rs.getString("NOMBRE"));
            data.put("estado", rs.getString("ESTADO"));
            return data;
        }
    }

    private static Map<String, Object> eleccionPorId(Connection conn, Long idEleccion) throws SQLException {
        String sql = "SELECT ID_ELECCION, NOMBRE, ESTADO FROM Elecciones WHERE ID_ELECCION = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? eleccionRow(rs) : null;
            }
        }
    }

    private static Map<String, Object> ultimaEleccion(Connection conn) throws SQLException {
        String inicioCol = columnExists(conn, "ELECCIONES", "FECHAHORA_INICIO") ? "FECHAHORA_INICIO" : "FECHA_HORA_INICIO";
        String sql = "SELECT * FROM (SELECT ID_ELECCION, NOMBRE, ESTADO FROM Elecciones ORDER BY " + inicioCol + " DESC) WHERE ROWNUM <= 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? eleccionRow(rs) : null;
        }
    }

    private static Map<String, Object> eleccionRow(ResultSet rs) throws SQLException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", rs.getLong("ID_ELECCION"));
        data.put("nombre", rs.getString("NOMBRE"));
        data.put("estado", rs.getString("ESTADO"));
        return data;
    }

    private static List<Map<String, Object>> resultadosPorCandidato(Connection conn, Long idEleccion) throws SQLException {
        String sql = "SELECT ce.ID_CANDIDATO, ce.NUMERO_CAMPANIA, ce.CARGO, c.PRIMER_NOMBRE, c.SEGUNDO_NOMBRE, " +
                "c.PRIMER_APELLIDO, c.SEGUNDO_APELLIDO, COUNT(v.ID_CANDIDATO) AS VOTOS, " +
                "NVL(SUM(v." + pesoColumn(conn) + "), 0) AS VOTOS_PONDERADOS " +
                "FROM Candidatos_eleccion ce JOIN Candidatos c ON c.ID_CANDIDATO = ce.ID_CANDIDATO " +
                "LEFT JOIN Votos v ON v.ID_ELECCION = ce.ID_ELECCION AND v.ID_CANDIDATO = ce.ID_CANDIDATO " +
                "WHERE ce.ID_ELECCION = ? " +
                "GROUP BY ce.ID_CANDIDATO, ce.NUMERO_CAMPANIA, ce.CARGO, c.PRIMER_NOMBRE, c.SEGUNDO_NOMBRE, c.PRIMER_APELLIDO, c.SEGUNDO_APELLIDO " +
                "ORDER BY VOTOS DESC, ce.CARGO, ce.NUMERO_CAMPANIA";
        List<Map<String, Object>> rows = queryRows(conn, sql, idEleccion, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("idCandidato", rs.getLong("ID_CANDIDATO"));
            row.put("numeroCampania", rs.getInt("NUMERO_CAMPANIA"));
            row.put("cargo", rs.getString("CARGO"));
            row.put("nombre", nombre(rs));
            row.put("votos", rs.getLong("VOTOS"));
            row.put("votosPonderados", rs.getDouble("VOTOS_PONDERADOS"));
            return row;
        });
        Map<String, Object> blanco = resultadoVotoEnBlanco(conn, idEleccion);
        if (blanco != null) {
            rows.add(blanco);
        }
        return rows;
    }

    private static Map<String, Object> resultadoVotoEnBlanco(Connection conn, Long idEleccion) throws SQLException {
        String sql = "SELECT COUNT(*) AS VOTOS, NVL(SUM(" + pesoColumn(conn) + "), 0) AS VOTOS_PONDERADOS " +
                "FROM Votos WHERE ID_ELECCION = ? AND ID_CANDIDATO IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getLong("VOTOS") == 0) {
                    return null;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("idCandidato", null);
                row.put("numeroCampania", 0);
                row.put("cargo", "VOTO EN BLANCO");
                row.put("nombre", "VOTO EN BLANCO");
                row.put("votos", rs.getLong("VOTOS"));
                row.put("votosPonderados", rs.getDouble("VOTOS_PONDERADOS"));
                row.put("votoBlanco", true);
                return row;
            }
        }
    }

    private static List<Map<String, Object>> resultadosPorRol(Connection conn, Long idEleccion) throws SQLException {
        // Votos es anonimo por diseno: no almacena identificacion ni id_rol.
        // El peso ponderado por rol se obtiene desde Eleccion_roles, que define
        // cuantos votos ponderados aporta cada rol a la eleccion.
        String sql = "SELECT r.NOMBRE AS ROL, er.PESO_VOTO, " +
                "(SELECT COUNT(*) FROM Registro_votos rv " +
                " JOIN Votantes vt ON vt.IDENTIFICACION = rv.IDENTIFICACION " +
                " WHERE rv.ID_ELECCION = er.ID_ELECCION AND vt.ID_ROL = er.ID_ROL) AS PARTICIPANTES " +
                "FROM Eleccion_roles er " +
                "JOIN Roles r ON r.ID_ROL = er.ID_ROL " +
                "WHERE er.ID_ELECCION = ? " +
                "ORDER BY PARTICIPANTES DESC";
        return queryRows(conn, sql, idEleccion, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rol", rs.getString("ROL"));
            row.put("pesoVoto", rs.getDouble("PESO_VOTO"));
            row.put("participantes", rs.getLong("PARTICIPANTES"));
            row.put("nota", "Votos anonimos: no se vincula candidato con rol del votante");
            return row;
        });
    }

    private static List<Map<String, Object>> participacionPorRol(Connection conn, Long idEleccion) throws SQLException {
        String sql = "SELECT r.NOMBRE AS ROL, COUNT(*) AS TOTAL FROM Registro_votos rv " +
                "JOIN Votantes v ON v.IDENTIFICACION = rv.IDENTIFICACION JOIN Roles r ON r.ID_ROL = v.ID_ROL " +
                "WHERE rv.ID_ELECCION = ? GROUP BY r.NOMBRE ORDER BY TOTAL DESC";
        return queryRows(conn, sql, idEleccion, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rol", rs.getString("ROL"));
            row.put("total", rs.getLong("TOTAL"));
            return row;
        });
    }

    private static List<Map<String, Object>> participacionPorPuesto(Connection conn, Long idEleccion) throws SQLException {
        String rvPuesto = columnExists(conn, "REGISTRO_VOTOS", "ID_PUESTO") ? "ID_PUESTO" : "ID_PUESTOS";
        String pPuesto = columnExists(conn, "PUESTOS_VOTACION", "ID_PUESTO") ? "ID_PUESTO" : "ID_PUESTOS";
        String sql = "SELECT p.NOMBRE_PUESTO, p.CIUDAD, COUNT(*) AS TOTAL FROM Registro_votos rv " +
                "LEFT JOIN Puestos_votacion p ON p." + pPuesto + " = rv." + rvPuesto + " " +
                "WHERE rv.ID_ELECCION = ? GROUP BY p.NOMBRE_PUESTO, p.CIUDAD ORDER BY TOTAL DESC";
        return queryRows(conn, sql, idEleccion, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("puesto", rs.getString("NOMBRE_PUESTO"));
            row.put("ciudad", rs.getString("CIUDAD"));
            row.put("total", rs.getLong("TOTAL"));
            return row;
        });
    }

    private static List<Map<String, Object>> participacionPorCiudad(Connection conn, Long idEleccion) throws SQLException {
        String rvPuesto = columnExists(conn, "REGISTRO_VOTOS", "ID_PUESTO") ? "ID_PUESTO" : "ID_PUESTOS";
        String pPuesto = columnExists(conn, "PUESTOS_VOTACION", "ID_PUESTO") ? "ID_PUESTO" : "ID_PUESTOS";
        String sql = "SELECT p.CIUDAD, COUNT(*) AS TOTAL FROM Registro_votos rv " +
                "LEFT JOIN Puestos_votacion p ON p." + pPuesto + " = rv." + rvPuesto + " " +
                "WHERE rv.ID_ELECCION = ? GROUP BY p.CIUDAD ORDER BY TOTAL DESC";
        return queryRows(conn, sql, idEleccion, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ciudad", rs.getString("CIUDAD"));
            row.put("total", rs.getLong("TOTAL"));
            return row;
        });
    }

    private interface RowMapper {
        Map<String, Object> map(ResultSet rs) throws SQLException;
    }

    private interface MetricSupplier {
        List<Map<String, Object>> get() throws SQLException;
    }

    private static List<Map<String, Object>> safeMetric(List<String> advertencias, String nombre, MetricSupplier supplier) {
        try {
            return supplier.get();
        } catch (SQLException e) {
            String message = nombre + ": " + e.getMessage();
            System.err.println("[VotacionController] " + message);
            advertencias.add(message);
            return List.of();
        }
    }

    private static List<Map<String, Object>> queryRows(Connection conn, String sql, Long idEleccion, RowMapper mapper) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
            }
        }
        return rows;
    }

    private static List<Map<String, Object>> candidatosPorCargo(Connection conn, Long idEleccion) throws SQLException {
        String fotoSelect = columnExists(conn, "CANDIDATOS", "FOTO_URL")
                ? "c.FOTO_URL"
                : "CAST(NULL AS VARCHAR2(500)) AS FOTO_URL";
        String sql = "SELECT ce.ID_CANDIDATO, ce.NUMERO_CAMPANIA, ce.CARGO, c.PRIMER_NOMBRE, c.SEGUNDO_NOMBRE, " +
                "c.PRIMER_APELLIDO, c.SEGUNDO_APELLIDO, " + fotoSelect + " FROM Candidatos_eleccion ce " +
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
                    candidato.put("fotoUrl", rs.getString("FOTO_URL"));
                    grupos.computeIfAbsent(cargo, k -> new ArrayList<>()).add(candidato);
                }
            }
        }
        if (grupos.isEmpty()) {
            grupos.put("VOTO EN BLANCO", new ArrayList<>());
        }
        List<Map<String, Object>> cargos = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grupos.entrySet()) {
            entry.getValue().add(votoEnBlanco(entry.getKey()));
            cargos.add(Map.of("cargo", entry.getKey(), "candidatos", entry.getValue()));
        }
        return cargos;
    }

    private static Map<String, Object> votoEnBlanco(String cargo) {
        Map<String, Object> candidato = new LinkedHashMap<>();
        candidato.put("idCandidato", null);
        candidato.put("numeroCampania", "BLANCO");
        candidato.put("nombre", "VOTO EN BLANCO");
        candidato.put("cargo", cargo);
        candidato.put("votoBlanco", true);
        return candidato;
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

    private static long count(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static long countById(Connection conn, String sql, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static String pesoColumn(Connection conn) throws SQLException {
        return columnExists(conn, "VOTOS", "PESO_VOTO_APLICADO") ? "PESO_VOTO_APLICADO" : "PESOVOTO_APLICADO";
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean handleOracle(Context ctx, Throwable e) {
        return OracleErrorHandler.from(e).map(error -> {
            ctx.status(error.statusCode()).json(Map.of("error", error.message(), "oraCode", error.oraCode()));
            return true;
        }).orElse(false);
    }
}
