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
                "SELECT seq_auditoria_correos.NEXTVAL, v.IDENTIFICACION, ?, v.CORREO, ?, 'RESEND', ? " +
                "FROM Votantes v WHERE v.IDENTIFICACION = ?";

        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"ID_AUDITORIA"})) {
            ps.setLong(1, idEleccion);
            ps.setString(2, ESTADO_SOLICITADO);
            ps.setString(3, codigoCertificado);
            ps.setString(4, identificacion);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new IllegalArgumentException("No existe votante para auditar certificado: " + identificacion);
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaCorreoRepository.registrarSolicitud", e);
        }
        return findUltimaPorVotanteEleccion(identificacion, idEleccion)
                .map(AuditoriaCorreo::getId)
                .orElseThrow(() -> new RuntimeException("No fue posible obtener la auditoria de correo creada"));
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
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaCorreoRepository.findById - id: " + id, e);
        }
    }

    public Optional<AuditoriaCorreo> findUltimaPorVotanteEleccion(String identificacion, Long idEleccion) {
        String sql = "SELECT * FROM (" + selectBase() +
                " WHERE IDENTIFICACION = ? AND ID_ELECCION = ? ORDER BY FECHA_SOLICITUD DESC) WHERE ROWNUM = 1";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ps.setLong(2, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaCorreoRepository.findUltimaPorVotanteEleccion", e);
        }
    }

    public List<AuditoriaCorreo> findByEleccion(Long idEleccion) {
        String sql = selectBase() + " WHERE ID_ELECCION = ? ORDER BY FECHA_SOLICITUD DESC";
        List<AuditoriaCorreo> auditorias = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    auditorias.add(mapRow(rs));
                }
                return auditorias;
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
        auditoria.setId(rs.getLong("ID_AUDITORIA"));
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
