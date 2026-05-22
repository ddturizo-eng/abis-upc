package com.abisupc.repository;

import com.abisupc.config.AppConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnvioContingenciaRepository {

    public void registrar(Long idToken, String identificacion, Long idEleccion, String correoDestino,
                          String estadoEnvio, String messageId, String errorEnvio) {
        String sql = """
                INSERT INTO envios_contingencia (
                    id_envio, id_token, identificacion, id_eleccion, correo_destino,
                    estado_envio, message_id, error_envio, fecha_intento, intento_numero
                ) VALUES (
                    seq_envios_contingencia.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP, ?
                )
                """;
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idToken);
            ps.setString(2, identificacion);
            ps.setLong(3, idEleccion);
            ps.setString(4, correoDestino);
            ps.setString(5, estadoEnvio);
            ps.setString(6, messageId);
            ps.setString(7, truncate(errorEnvio, 1000));
            ps.setInt(8, siguienteIntento(conn, identificacion, idEleccion));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error registrando envio de contingencia", e);
        }
    }

    public Map<String, Object> resumen(Long idEleccion) {
        try (Connection conn = AppConfig.getConnection()) {
            long elegibles = count(conn, "SELECT COUNT(*) FROM votantes WHERE UPPER(estado_voto) = 'PENDIENTE'");
            long tokens = count(conn, "SELECT COUNT(*) FROM tokens_contingencia WHERE id_eleccion = ?", idEleccion);
            long enviados = countUltimoEstado(conn, idEleccion, "ENVIADO");
            long fallidos = countUltimoEstado(conn, idEleccion, "FALLIDO");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("elegibles", elegibles);
            data.put("tokens", tokens);
            data.put("enviados", enviados);
            data.put("fallidos", fallidos);
            data.put("pendientes", Math.max(0, tokens - enviados - fallidos));
            return data;
        } catch (SQLException e) {
            throw new RuntimeException("Error consultando resumen de contingencia", e);
        }
    }

    public List<Map<String, Object>> listarTokens(Long idEleccion, String estadoEnvio) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.id_token, t.identificacion, t.id_eleccion, t.token_hint, t.estado,
                       t.fecha_emision, t.fecha_uso, v.correo, v.primer_nombre, v.segundo_nombre,
                       v.primer_apellido, v.segundo_apellido, ultimo.estado_envio, ultimo.fecha_intento,
                       ultimo.message_id, ultimo.error_envio, ultimo.intento_numero
                FROM tokens_contingencia t
                JOIN votantes v ON v.identificacion = t.identificacion
                LEFT JOIN (
                    SELECT e.*
                    FROM envios_contingencia e
                    JOIN (
                        SELECT id_token, MAX(fecha_intento) fecha_intento
                        FROM envios_contingencia
                        GROUP BY id_token
                    ) m ON m.id_token = e.id_token AND m.fecha_intento = e.fecha_intento
                ) ultimo ON ultimo.id_token = t.id_token
                WHERE t.id_eleccion = ?
                """);
        if (estadoEnvio != null && !estadoEnvio.isBlank()) {
            sql.append(" AND NVL(ultimo.estado_envio, 'PENDIENTE') = ? ");
        }
        sql.append(" ORDER BY v.primer_apellido, v.primer_nombre");

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setLong(1, idEleccion);
            if (estadoEnvio != null && !estadoEnvio.isBlank()) {
                ps.setString(2, estadoEnvio.trim().toUpperCase());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(tokenRow(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando tokens de contingencia", e);
        }
    }

    public List<Map<String, Object>> historial(Long idEleccion, int limit) {
        String sql = """
                SELECT * FROM (
                    SELECT e.id_envio, e.id_token, e.identificacion, e.id_eleccion, e.correo_destino,
                           e.estado_envio, e.message_id, e.error_envio, e.fecha_intento, e.intento_numero
                    FROM envios_contingencia e
                    WHERE (? IS NULL OR e.id_eleccion = ?)
                    ORDER BY e.fecha_intento DESC
                ) WHERE ROWNUM <= ?
                """;
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (idEleccion == null) {
                ps.setNull(1, java.sql.Types.NUMERIC);
                ps.setNull(2, java.sql.Types.NUMERIC);
            } else {
                ps.setLong(1, idEleccion);
                ps.setLong(2, idEleccion);
            }
            ps.setInt(3, Math.max(1, Math.min(limit, 500)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("idEnvio", rs.getLong("ID_ENVIO"));
                    row.put("idToken", rs.getLong("ID_TOKEN"));
                    row.put("identificacion", rs.getString("IDENTIFICACION"));
                    row.put("idEleccion", rs.getLong("ID_ELECCION"));
                    row.put("correoDestino", rs.getString("CORREO_DESTINO"));
                    row.put("estadoEnvio", rs.getString("ESTADO_ENVIO"));
                    row.put("messageId", rs.getString("MESSAGE_ID"));
                    row.put("errorEnvio", rs.getString("ERROR_ENVIO"));
                    row.put("fechaIntento", text(rs.getTimestamp("FECHA_INTENTO")));
                    row.put("intentoNumero", rs.getInt("INTENTO_NUMERO"));
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error consultando auditoria de contingencia", e);
        }
    }

    private int siguienteIntento(Connection conn, String identificacion, Long idEleccion) throws SQLException {
        String sql = "SELECT NVL(MAX(INTENTO_NUMERO), 0) + 1 FROM envios_contingencia WHERE identificacion = ? AND id_eleccion = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ps.setLong(2, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    private long count(Connection conn, String sql, Long... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setLong(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private long countUltimoEstado(Connection conn, Long idEleccion, String estadoEnvio) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM tokens_contingencia t
                JOIN (
                    SELECT e.*
                    FROM envios_contingencia e
                    JOIN (
                        SELECT id_token, MAX(fecha_intento) fecha_intento
                        FROM envios_contingencia
                        GROUP BY id_token
                    ) m ON m.id_token = e.id_token AND m.fecha_intento = e.fecha_intento
                ) ultimo ON ultimo.id_token = t.id_token
                WHERE t.id_eleccion = ? AND ultimo.estado_envio = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            ps.setString(2, estadoEnvio);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private Map<String, Object> tokenRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("idToken", rs.getLong("ID_TOKEN"));
        row.put("identificacion", rs.getString("IDENTIFICACION"));
        row.put("idEleccion", rs.getLong("ID_ELECCION"));
        row.put("tokenHint", rs.getString("TOKEN_HINT"));
        row.put("estado", rs.getString("ESTADO"));
        row.put("fechaEmision", text(rs.getTimestamp("FECHA_EMISION")));
        row.put("fechaUso", text(rs.getTimestamp("FECHA_USO")));
        row.put("correo", rs.getString("CORREO"));
        row.put("nombre", nombre(rs));
        row.put("estadoEnvio", rs.getString("ESTADO_ENVIO") != null ? rs.getString("ESTADO_ENVIO") : "PENDIENTE");
        row.put("fechaIntento", text(rs.getTimestamp("FECHA_INTENTO")));
        row.put("messageId", rs.getString("MESSAGE_ID"));
        row.put("errorEnvio", rs.getString("ERROR_ENVIO"));
        row.put("intentoNumero", rs.getInt("INTENTO_NUMERO"));
        return row;
    }

    private String nombre(ResultSet rs) throws SQLException {
        return String.join(" ",
                safe(rs.getString("PRIMER_NOMBRE")),
                safe(rs.getString("SEGUNDO_NOMBRE")),
                safe(rs.getString("PRIMER_APELLIDO")),
                safe(rs.getString("SEGUNDO_APELLIDO"))
        ).replaceAll("\\s+", " ").trim();
    }

    private String text(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
