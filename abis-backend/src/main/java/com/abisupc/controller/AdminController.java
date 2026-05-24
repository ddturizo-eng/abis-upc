package com.abisupc.controller;

import com.abisupc.config.AppConfig;
import com.abisupc.dto.ApiResponse;
import com.abisupc.model.Administrador;
import com.abisupc.model.AuditoriaVotante;
import com.abisupc.repository.AuditoriaVotanteRepository;
import com.abisupc.service.AdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdminController {

    private static final AdminService service = new AdminService();
    private static final AuditoriaVotanteRepository auditoriaRepo = new AuditoriaVotanteRepository();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void login(Context ctx) {
        try {
            ObjectNode body = mapper.readValue(ctx.body(), ObjectNode.class);

            String usuario = body.has("usuario") ? body.get("usuario").asText() : null;
            String password = body.has("password") ? body.get("password").asText() : null;

            if (usuario == null || usuario.isBlank() || password == null || password.isBlank()) {
                ctx.status(400).json(errorJson("Usuario y contraseña son requeridos"));
                return;
            }

            AdminService.LoginResult result = service.login(usuario, password);

            if (result.success) {
                ObjectNode response = mapper.createObjectNode();
                response.put("success", true);
                response.put("token", result.token);
                response.put("message", result.message);
                ctx.json(response.toString());
            } else {
                ctx.status(401).json(errorJson(result.message));
            }

        } catch (Exception e) {
            System.err.println("[AdminController] Error login: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).json(errorJson("Error interno del servidor: " + e.getMessage()));
        }
    }

    public static void logout(Context ctx) {
        try {
            String token = ctx.header("Authorization");

            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            if (token == null || token.isBlank()) {
                // try body
                try {
                    ObjectNode body = mapper.readValue(ctx.body(), ObjectNode.class);
                    token = body.has("token") ? body.get("token").asText() : null;
                } catch (Exception ignored) {
                }
            }

            if (token == null || token.isBlank()) {
                ctx.status(400).json(errorJson("Token requerido para cerrar sesion"));
                return;
            }

            boolean ok = service.logout(token);
            ObjectNode response = mapper.createObjectNode();
            response.put("success", ok);
            response.put("message", ok ? "Sesion cerrada exitosamente" : "Token invalido o sesion ya finalizada");
            ctx.json(response.toString());

        } catch (Exception e) {
            System.err.println("[AdminController] Error logout: " + e.getMessage());
            ctx.status(500).json(errorJson("Error interno del servidor"));
        }
    }

    public static void dashboard(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            Long idAdmin = ctx.attribute("idAdmin");
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("admin", getAdmin(ctx, idAdmin));
            Map<String, Object> censo = getCenso(conn);
            response.put("censo", censo);

            Map<String, Object> eleccionActiva = getEleccionActiva(conn);
            response.put("eleccionActiva", eleccionActiva);
            response.put("proximaEleccion", getProximaEleccion(conn));
            response.put("votosEmitidos", eleccionActiva != null
                    ? countById(conn, "SELECT COUNT(*) FROM Registro_votos WHERE ID_ELECCION = ?",
                    ((Number) eleccionActiva.get("id")).longValue())
                    : 0L);

            ctx.json(response);
        } catch (Exception e) {
            System.err.println("[AdminController] Error dashboard: " + e.getMessage());
            ctx.status(500).json(ApiResponse.error("Error al cargar dashboard: " + e.getMessage()));
        }
    }

    public static void auditoriaReciente(Context ctx) {
        int limite = 10;
        try {
            String limiteParam = ctx.queryParam("limit");
            if (limiteParam == null) {
                limiteParam = ctx.queryParam("limite");
            }
            limite = Math.max(1, Math.min(50, Integer.parseInt(limiteParam != null ? limiteParam : "10")));
        } catch (NumberFormatException ignored) {
        }

        try {
            List<Map<String, Object>> data = auditoriaRepo.findRecientesDelDia(limite).stream()
                    .map(AdminController::auditoriaToResponse)
                    .toList();
            ctx.json(ApiResponse.success(data));
        } catch (Exception e) {
            System.err.println("[AdminController] Error auditoria reciente: " + e.getMessage());
            ctx.status(500).json(ApiResponse.error("Error al cargar auditoria: " + e.getMessage()));
        }
    }

    public static void estadisticasVotantes(Context ctx) {
        String sql = "SELECT " +
                "COUNT(*) AS TOTAL, " +
                "(SELECT COUNT(DISTINCT IDENTIFICACION) FROM Registro_votos) AS VOTARON, " +
                "SUM(CASE WHEN UPPER(ESTADO_VOTO) = 'PENDIENTE' THEN 1 ELSE 0 END) AS PENDIENTES, " +
                "SUM(CASE WHEN UPPER(ESTADO_VOTO) = 'INHABILITADO' THEN 1 ELSE 0 END) AS INHABILITADOS " +
                "FROM Votantes";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            Map<String, Object> data = new LinkedHashMap<>();
            if (rs.next()) {
                long total = rs.getLong("TOTAL");
                long votaron = rs.getLong("VOTARON");
                long pendientes = rs.getLong("PENDIENTES");
                long inhabilitados = rs.getLong("INHABILITADOS");
                data.put("total", total);
                data.put("votaron", votaron);
                data.put("ejercidos", votaron);
                data.put("pendientes", pendientes);
                data.put("inhabilitados", inhabilitados);
                data.put("participacion", total > 0 ? Math.round((votaron * 100.0) / total) : 0);
            } else {
                data.put("total", 0);
                data.put("votaron", 0);
                data.put("ejercidos", 0);
                data.put("pendientes", 0);
                data.put("inhabilitados", 0);
                data.put("participacion", 0);
            }
            ctx.json(data);
        } catch (Exception e) {
            System.err.println("[AdminController] Error estadisticas votantes: " + e.getMessage());
            ctx.status(500).json(ApiResponse.error("Error al cargar estadisticas: " + e.getMessage()));
        }
    }

    private static Map<String, Object> getAdmin(Context ctx, Long idAdmin) {
        String token = ctx.attribute("token");
        Optional<Administrador> optAdmin = idAdmin != null
                ? service.getAdminByToken(token)
                : Optional.empty();
        Map<String, Object> admin = new LinkedHashMap<>();
        admin.put("nombre", optAdmin.map(Administrador::getNombre).orElse("Administrador"));
        admin.put("usuario", optAdmin.map(Administrador::getUsuario).orElse(""));
        return admin;
    }

    private static Map<String, Object> auditoriaToResponse(AuditoriaVotante auditoria) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("idAuditoria", auditoria.getId());
        row.put("identificacion", auditoria.getIdentificacion());
        row.put("idAdmin", auditoria.getIdAdmin());
        row.put("campoModificado", auditoria.getCampoModificado());
        row.put("valorAnterior", auditoria.getValorAnterior());
        row.put("valorNuevo", auditoria.getValorNuevo());
        row.put("motivo", auditoria.getMotivo());
        row.put("accion", auditoria.getAccion());
        row.put("fechaHora", toIso(auditoria.getFechaHora()));
        row.put("nombreAdmin", auditoria.getNombreAdmin());
        return row;
    }

    private static Map<String, Object> getCenso(Connection conn) throws SQLException {
        Map<String, Object> censo = new LinkedHashMap<>();
        long total = count(conn, "SELECT COUNT(*) FROM Votantes");
        long conBiometria = count(conn, "SELECT COUNT(*) FROM Biometria_votantes WHERE ACTIVO = 'S'");
        censo.put("total", total);
        censo.put("porRol", getCensoPorRol(conn));
        censo.put("conBiometria", conBiometria);
        censo.put("sinBiometria", Math.max(0, total - conBiometria));
        censo.put("pendientes", count(conn, "SELECT COUNT(*) FROM Votantes WHERE UPPER(ESTADO_VOTO) = 'PENDIENTE'"));
        censo.put("ejercidos", count(conn, "SELECT COUNT(DISTINCT IDENTIFICACION) FROM Registro_votos"));
        censo.put("inhabilitados", count(conn, "SELECT COUNT(*) FROM Votantes WHERE UPPER(ESTADO_VOTO) = 'INHABILITADO'"));
        return censo;
    }

    private static List<Map<String, Object>> getCensoPorRol(Connection conn) throws SQLException {
        String sql = "SELECT r.ID_ROL, r.NOMBRE, COUNT(v.IDENTIFICACION) AS TOTAL " +
                "FROM Roles r LEFT JOIN Votantes v ON v.ID_ROL = r.ID_ROL " +
                "GROUP BY r.ID_ROL, r.NOMBRE ORDER BY r.ID_ROL";
        List<Map<String, Object>> roles = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> rol = new LinkedHashMap<>();
                rol.put("idRol", rs.getLong("ID_ROL"));
                rol.put("nombre", rs.getString("NOMBRE"));
                rol.put("total", rs.getLong("TOTAL"));
                roles.add(rol);
            }
        }
        return roles;
    }

    private static Map<String, Object> getEleccionActiva(Connection conn) throws SQLException {
        return getEleccionBySql(conn, "EN_CURSO", false);
    }

    private static Map<String, Object> getProximaEleccion(Connection conn) throws SQLException {
        return getEleccionBySql(conn, "PROGRAMADA", true);
    }

    private static Map<String, Object> getEleccionBySql(Connection conn, String estado, boolean asc) throws SQLException {
        String inicioCol = columnExists(conn, "ELECCIONES", "FECHAHORA_INICIO") ? "FECHAHORA_INICIO" : "FECHA_HORA_INICIO";
        String finCol = columnExists(conn, "ELECCIONES", "FECHAHORA_FIN") ? "FECHAHORA_FIN" : "FECHA_HORA_FIN";
        String sql = "SELECT * FROM (SELECT ID_ELECCION, NOMBRE, " + inicioCol + " AS FECHA_INICIO, " +
                finCol + " AS FECHA_FIN, ESTADO FROM Elecciones WHERE ESTADO = ? ORDER BY " +
                inicioCol + (asc ? " ASC" : " DESC") + ") WHERE ROWNUM <= 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> eleccion = new LinkedHashMap<>();
                eleccion.put("id", rs.getLong("ID_ELECCION"));
                eleccion.put("idEleccion", rs.getLong("ID_ELECCION"));
                eleccion.put("nombre", rs.getString("NOMBRE"));
                eleccion.put("fechaHoraInicio", toIso(rs.getTimestamp("FECHA_INICIO")));
                eleccion.put("fechaHoraFin", toIso(rs.getTimestamp("FECHA_FIN")));
                eleccion.put("estado", rs.getString("ESTADO"));
                return eleccion;
            }
        }
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

    private static String toIso(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime dateTime = timestamp.toLocalDateTime();
        return dateTime.toString();
    }

    private static String errorJson(String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("success", false);
        node.put("message", message);
        return node.toString();
    }
}
