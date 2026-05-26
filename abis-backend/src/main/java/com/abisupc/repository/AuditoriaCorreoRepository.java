package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.AuditoriaCorreo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Auditoria operativa del envio de certificados. El correo se toma siempre
 * desde Votantes para evitar destinos enviados desde el frontend.
 */
public class AuditoriaCorreoRepository {

    private static final String ESTADO_SOLICITADO = "SOLICITADO";
    private static final String ESTADO_ENVIADO = "ENVIADO";
    private static final String ESTADO_ERROR = "ERROR";
    private static final String ESTADO_PENDIENTE_REINTENTO = "PENDIENTE_REINTENTO";

    public Long registrarSolicitud(String identificacion, Long idEleccion, String codigoCertificado) {
        validarSolicitud(identificacion, idEleccion, codigoCertificado);
        String sql = "INSERT INTO Auditoria_correos (" +
                "ID_AUDITORIA, IDENTIFICACION, ID_ELECCION, CORREO_VOTANTE, ESTADO, PROVIDER, CODIGO_CERTIFICADO) " +
                "SELECT ?, v.IDENTIFICACION, ?, v.CORREO, ?, 'RESEND', ? " +
                "FROM Votantes v WHERE v.IDENTIFICACION = ?";

        try (Connection conn = AppConfig.getConnection()) {
            asegurarInfraestructura(conn);
            Long idAuditoria = siguienteId(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, idAuditoria);
                ps.setLong(2, idEleccion);
                ps.setString(3, ESTADO_SOLICITADO);
                ps.setString(4, codigoCertificado);
                ps.setString(5, identificacion);
                int filas = ps.executeUpdate();
                if (filas == 0) {
                    throw new IllegalArgumentException("No existe votante para auditar certificado: " + identificacion);
                }
                return idAuditoria;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaCorreoRepository.registrarSolicitud", e);
        }
    }

    public Long registrarReintento(AuditoriaCorreo auditoriaOriginal) {
        if (auditoriaOriginal == null) {
            throw new IllegalArgumentException("auditoria requerida");
        }
        validarSolicitud(
                auditoriaOriginal.getIdentificacion(),
                auditoriaOriginal.getIdEleccion(),
                auditoriaOriginal.getCodigoCertificado()
        );
        String sql = "INSERT INTO Auditoria_correos (" +
                "ID_AUDITORIA, IDENTIFICACION, ID_ELECCION, CORREO_VOTANTE, ESTADO, PROVIDER, CODIGO_CERTIFICADO, OBSERVACIONES) " +
                "SELECT ?, v.IDENTIFICACION, ?, v.CORREO, ?, 'RESEND', ?, ? " +
                "FROM Votantes v WHERE v.IDENTIFICACION = ?";

        try (Connection conn = AppConfig.getConnection()) {
            asegurarInfraestructura(conn);
            Long idAuditoria = siguienteId(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, idAuditoria);
                ps.setLong(2, auditoriaOriginal.getIdEleccion());
                ps.setString(3, ESTADO_PENDIENTE_REINTENTO);
                ps.setString(4, auditoriaOriginal.getCodigoCertificado());
                String origen = auditoriaOriginal.getId() != null
                        ? "Reenvio solicitado desde panel. Auditoria origen: " + auditoriaOriginal.getId()
                        : "Reenvio solicitado desde panel";
                ps.setString(5, origen);
                ps.setString(6, auditoriaOriginal.getIdentificacion());
                int filas = ps.executeUpdate();
                if (filas == 0) {
                    throw new IllegalArgumentException("No existe votante para auditar certificado: " + auditoriaOriginal.getIdentificacion());
                }
                return idAuditoria;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaCorreoRepository.registrarReintento", e);
        }
    }

    public Long registrarReintento(String identificacion, Long idEleccion, String codigoCertificado) {
        validarSolicitud(identificacion, idEleccion, codigoCertificado);
        AuditoriaCorreo auditoria = new AuditoriaCorreo();
        auditoria.setIdentificacion(identificacion);
        auditoria.setIdEleccion(idEleccion);
        auditoria.setCodigoCertificado(codigoCertificado);
        return registrarReintento(auditoria);
    }

    public List<AuditoriaCorreo> findRecientes(Long idEleccion, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String sql = "SELECT * FROM (" + selectBase() +
                (idEleccion != null && idEleccion > 0 ? " WHERE ID_ELECCION = ?" : "") +
                " ORDER BY FECHA_SOLICITUD DESC) WHERE ROWNUM <= ?";
        List<AuditoriaCorreo> auditorias = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection()) {
            asegurarInfraestructura(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int index = 1;
                if (idEleccion != null && idEleccion > 0) {
                    ps.setLong(index++, idEleccion);
                }
                ps.setInt(index, safeLimit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        auditorias.add(mapRow(rs));
                    }
                }
            }
            return auditorias;
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaCorreoRepository.findRecientes", e);
        }
    }

    public List<AuditoriaCorreo> findUltimasPorRegistrosVoto(Long idEleccion, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String sql = "SELECT * FROM (" +
                "SELECT a.ID_AUDITORIA, rv.IDENTIFICACION, rv.ID_ELECCION, v.CORREO AS CORREO_VOTANTE, " +
                "a.FECHA_SOLICITUD, a.FECHA_ENVIO, NVL(a.ESTADO, 'SIN_SOLICITUD') AS ESTADO, " +
                "a.PROVIDER, a.MESSAGE_ID, a.CODIGO_CERTIFICADO, a.OBSERVACIONES, " +
                "v.PRIMER_NOMBRE || ' ' || NVL(v.PRIMER_APELLIDO, '') AS NOMBRE_VOTANTE, " +
                "e.NOMBRE AS NOMBRE_ELECCION " +
                "FROM Registro_votos rv " +
                "JOIN Votantes v ON v.IDENTIFICACION = rv.IDENTIFICACION " +
                "JOIN Elecciones e ON e.ID_ELECCION = rv.ID_ELECCION " +
                "LEFT JOIN (" +
                "  SELECT ac.*, ROW_NUMBER() OVER (PARTITION BY ac.IDENTIFICACION, ac.ID_ELECCION ORDER BY ac.FECHA_SOLICITUD DESC) rn " +
                "  FROM Auditoria_correos ac" +
                ") a ON a.IDENTIFICACION = rv.IDENTIFICACION AND a.ID_ELECCION = rv.ID_ELECCION AND a.rn = 1 " +
                (idEleccion != null && idEleccion > 0 ? "WHERE rv.ID_ELECCION = ? " : "") +
                "ORDER BY NVL(a.FECHA_SOLICITUD, CAST(rv.FECHA_HORA AS TIMESTAMP)) DESC" +
                ") WHERE ROWNUM <= ?";

        List<AuditoriaCorreo> auditorias = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection()) {
            asegurarInfraestructura(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int index = 1;
                if (idEleccion != null && idEleccion > 0) {
                    ps.setLong(index++, idEleccion);
                }
                ps.setInt(index, safeLimit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        auditorias.add(mapRow(rs));
                    }
                }
            }
            return auditorias;
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaCorreoRepository.findUltimasPorRegistrosVoto", e);
        }
    }

    public Optional<AuditoriaCorreo> findUltimaPorVotanteEleccionORegistro(String identificacion, Long idEleccion) {
        Optional<AuditoriaCorreo> auditoria = findUltimaPorVotanteEleccion(identificacion, idEleccion);
        if (auditoria.isPresent()) {
            return auditoria;
        }

        String sql = "SELECT NULL AS ID_AUDITORIA, rv.IDENTIFICACION, rv.ID_ELECCION, v.CORREO AS CORREO_VOTANTE, " +
                "NULL AS FECHA_SOLICITUD, NULL AS FECHA_ENVIO, 'SIN_SOLICITUD' AS ESTADO, NULL AS PROVIDER, " +
                "NULL AS MESSAGE_ID, NULL AS CODIGO_CERTIFICADO, NULL AS OBSERVACIONES " +
                "FROM Registro_votos rv JOIN Votantes v ON v.IDENTIFICACION = rv.IDENTIFICACION " +
                "WHERE rv.IDENTIFICACION = ? AND rv.ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection()) {
            asegurarInfraestructura(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, identificacion);
                ps.setLong(2, idEleccion);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaCorreoRepository.findUltimaPorVotanteEleccionORegistro", e);
        }
    }

    public void asegurarInfraestructura() {
        try (Connection conn = AppConfig.getConnection()) {
            asegurarInfraestructura(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Error preparando Auditoria_correos", e);
        }
    }

    private Long siguienteId(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT seq_auditoria_correos.NEXTVAL FROM dual");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("No fue posible obtener seq_auditoria_correos.NEXTVAL");
    }

    private void asegurarInfraestructura(Connection conn) throws SQLException {
        if (!existeObjeto(conn, "AUDITORIA_CORREOS", "TABLE")) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE Auditoria_correos (" +
                            "ID_AUDITORIA NUMBER PRIMARY KEY, " +
                            "IDENTIFICACION VARCHAR2(20) NOT NULL, " +
                            "ID_ELECCION NUMBER NOT NULL, " +
                            "CORREO_VOTANTE VARCHAR2(255) NOT NULL, " +
                            "FECHA_SOLICITUD TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, " +
                            "FECHA_ENVIO TIMESTAMP NULL, " +
                            "ESTADO VARCHAR2(30) NOT NULL, " +
                            "PROVIDER VARCHAR2(40), " +
                            "MESSAGE_ID VARCHAR2(255), " +
                            "CODIGO_CERTIFICADO VARCHAR2(80), " +
                            "OBSERVACIONES VARCHAR2(500)" +
                            ")")) {
                ps.executeUpdate();
            }
        } else {
            asegurarColumna(conn, "ID_AUDITORIA", "NUMBER");
            asegurarColumna(conn, "IDENTIFICACION", "VARCHAR2(20)");
            asegurarColumna(conn, "ID_ELECCION", "NUMBER");
            asegurarColumna(conn, "CORREO_VOTANTE", "VARCHAR2(255)");
            asegurarColumna(conn, "FECHA_SOLICITUD", "TIMESTAMP DEFAULT SYSTIMESTAMP");
            asegurarColumna(conn, "FECHA_ENVIO", "TIMESTAMP");
            asegurarColumna(conn, "ESTADO", "VARCHAR2(30)");
            asegurarColumna(conn, "PROVIDER", "VARCHAR2(40)");
            asegurarColumna(conn, "MESSAGE_ID", "VARCHAR2(255)");
            asegurarColumna(conn, "CODIGO_CERTIFICADO", "VARCHAR2(80)");
            asegurarColumna(conn, "OBSERVACIONES", "VARCHAR2(500)");
        }

        if (!existeObjeto(conn, "SEQ_AUDITORIA_CORREOS", "SEQUENCE")) {
            long startWith = siguienteValorInicialSecuencia(conn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE SEQUENCE seq_auditoria_correos START WITH " + startWith + " INCREMENT BY 1 NOCACHE")) {
                ps.executeUpdate();
            }
        }
    }

    private void asegurarColumna(Connection conn, String columnName, String definition) throws SQLException {
        String sql = "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'AUDITORIA_CORREOS' AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("ALTER TABLE Auditoria_correos ADD (" + columnName + " " + definition + ")")) {
            ps.executeUpdate();
        }
    }

    private long siguienteValorInicialSecuencia(Connection conn) throws SQLException {
        if (!existeObjeto(conn, "AUDITORIA_CORREOS", "TABLE") || !existeColumna(conn, "ID_AUDITORIA")) {
            return 1L;
        }
        try (PreparedStatement ps = conn.prepareStatement("SELECT NVL(MAX(ID_AUDITORIA), 0) + 1 FROM Auditoria_correos");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Math.max(1L, rs.getLong(1)) : 1L;
        }
    }

    private boolean existeColumna(Connection conn, String columnName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'AUDITORIA_CORREOS' AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private boolean existeObjeto(Connection conn, String objectName, String objectType) throws SQLException {
        String sql = "SELECT COUNT(*) FROM USER_OBJECTS WHERE OBJECT_NAME = ? AND OBJECT_TYPE = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, objectName);
            ps.setString(2, objectType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public void marcarEnviado(Long idAuditoria, String messageId) {
        String sql = "UPDATE Auditoria_correos SET ESTADO = ?, FECHA_ENVIO = SYSTIMESTAMP, " +
                "MESSAGE_ID = ?, OBSERVACIONES = NULL WHERE ID_AUDITORIA = ?";
        actualizarEstado(idAuditoria, sql, ESTADO_ENVIADO, messageId, null);
    }

    public void marcarError(Long idAuditoria, String observaciones) {
        String sql = "UPDATE Auditoria_correos SET ESTADO = ?, OBSERVACIONES = ? WHERE ID_AUDITORIA = ?";
        actualizarEstado(idAuditoria, sql, ESTADO_ERROR, null, observaciones);
    }

    public void marcarPendienteReintento(Long idAuditoria, String observaciones) {
        String sql = "UPDATE Auditoria_correos SET ESTADO = ?, OBSERVACIONES = ? WHERE ID_AUDITORIA = ?";
        actualizarEstado(idAuditoria, sql, ESTADO_PENDIENTE_REINTENTO, null, observaciones);
    }

    public Optional<AuditoriaCorreo> findById(Long id) {
        String sql = selectBase() + " WHERE ID_AUDITORIA = ?";
        try (Connection conn = AppConfig.getConnection()) {
            asegurarInfraestructura(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaCorreoRepository.findById - id: " + id, e);
        }
    }

    public Optional<AuditoriaCorreo> findUltimaPorVotanteEleccion(String identificacion, Long idEleccion) {
        String sql = "SELECT * FROM (" + selectBase() +
                " WHERE IDENTIFICACION = ? AND ID_ELECCION = ? ORDER BY FECHA_SOLICITUD DESC) WHERE ROWNUM = 1";
        try (Connection conn = AppConfig.getConnection()) {
            asegurarInfraestructura(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, identificacion);
                ps.setLong(2, idEleccion);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaCorreoRepository.findUltimaPorVotanteEleccion", e);
        }
    }

    public List<AuditoriaCorreo> findByEleccion(Long idEleccion) {
        String sql = selectBase() + " WHERE ID_ELECCION = ? ORDER BY FECHA_SOLICITUD DESC";
        List<AuditoriaCorreo> auditorias = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection()) {
            asegurarInfraestructura(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, idEleccion);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        auditorias.add(mapRow(rs));
                    }
                    return auditorias;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaCorreoRepository.findByEleccion - idEleccion: " + idEleccion, e);
        }
    }

    private void actualizarEstado(Long idAuditoria, String sql, String estado, String messageId, String observaciones) {
        validarIdAuditoria(idAuditoria);
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado);
            if (ESTADO_ENVIADO.equals(estado)) {
                ps.setString(2, messageId);
            } else {
                ps.setString(2, limitarObservaciones(observaciones));
            }
            ps.setLong(3, idAuditoria);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new IllegalArgumentException("No existe auditoria de correo: " + idAuditoria);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaCorreoRepository.actualizarEstado", e);
        }
    }

    private AuditoriaCorreo mapRow(ResultSet rs) throws SQLException {
        AuditoriaCorreo auditoria = new AuditoriaCorreo();
        long idAuditoria = rs.getLong("ID_AUDITORIA");
        auditoria.setId(rs.wasNull() ? null : idAuditoria);
        auditoria.setIdentificacion(rs.getString("IDENTIFICACION"));
        auditoria.setIdEleccion(rs.getLong("ID_ELECCION"));
        auditoria.setCorreoVotante(rs.getString("CORREO_VOTANTE"));
        auditoria.setFechaSolicitud(rs.getTimestamp("FECHA_SOLICITUD"));
        auditoria.setFechaEnvio(rs.getTimestamp("FECHA_ENVIO"));
        auditoria.setEstado(rs.getString("ESTADO"));
        auditoria.setProvider(rs.getString("PROVIDER"));
        auditoria.setMessageId(rs.getString("MESSAGE_ID"));
        auditoria.setCodigoCertificado(rs.getString("CODIGO_CERTIFICADO"));
        auditoria.setObservaciones(rs.getString("OBSERVACIONES"));
        try { auditoria.setNombreCompleto(rs.getString("NOMBRE_VOTANTE")); } catch (SQLException ignored) {}
        try { auditoria.setNombreEleccion(rs.getString("NOMBRE_ELECCION")); } catch (SQLException ignored) {}
        return auditoria;
    }

    private String selectBase() {
        return "SELECT ID_AUDITORIA, IDENTIFICACION, ID_ELECCION, CORREO_VOTANTE, FECHA_SOLICITUD, " +
                "FECHA_ENVIO, ESTADO, PROVIDER, MESSAGE_ID, CODIGO_CERTIFICADO, OBSERVACIONES " +
                "FROM Auditoria_correos";
    }

    private void validarSolicitud(String identificacion, Long idEleccion, String codigoCertificado) {
        if (identificacion == null || identificacion.isBlank()) {
            throw new IllegalArgumentException("identificacion requerida");
        }
        if (idEleccion == null || idEleccion <= 0) {
            throw new IllegalArgumentException("idEleccion requerido");
        }
        if (codigoCertificado == null || codigoCertificado.isBlank()) {
            throw new IllegalArgumentException("codigoCertificado requerido");
        }
    }

    private void validarIdAuditoria(Long idAuditoria) {
        if (idAuditoria == null || idAuditoria <= 0) {
            throw new IllegalArgumentException("idAuditoria requerida");
        }
    }

    private String limitarObservaciones(String observaciones) {
        if (observaciones == null) {
            return null;
        }
        String limpia = observaciones.trim();
        return limpia.length() <= 500 ? limpia : limpia.substring(0, 500);
    }
}
