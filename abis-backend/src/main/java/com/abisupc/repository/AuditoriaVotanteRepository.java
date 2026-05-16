package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.AuditoriaVotante;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Consultas de auditoria de votantes. Las escrituras se generan desde Oracle.
 */
public class AuditoriaVotanteRepository {

    public Optional<AuditoriaVotante> findById(Long id) {
        String sql = selectBase() + " WHERE av.ID_AUDITORIA = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaVotanteRepository.findById - id: " + id, e);
        }
    }

    public List<AuditoriaVotante> findAll() {
        String sql = selectBase() + " ORDER BY av.FECHA_HORA DESC";
        List<AuditoriaVotante> auditorias = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                auditorias.add(mapRow(rs));
            }
            return auditorias;
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaVotanteRepository.findAll", e);
        }
    }

    public List<AuditoriaVotante> findRecientesDelDia(int limite) {
        String sql = "SELECT * FROM (" +
                selectBase() +
                " WHERE TRUNC(av.FECHA_HORA) = TRUNC(SYSDATE) " +
                "ORDER BY av.FECHA_HORA DESC) WHERE ROWNUM <= ?";
        List<AuditoriaVotante> auditorias = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, normalizarLimite(limite));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    auditorias.add(mapRow(rs));
                }
                return auditorias;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en AuditoriaVotanteRepository.findRecientesDelDia", e);
        }
    }

    private AuditoriaVotante mapRow(ResultSet rs) throws SQLException {
        AuditoriaVotante auditoria = new AuditoriaVotante();
        auditoria.setId(rs.getLong("ID_AUDITORIA"));
        auditoria.setIdentificacion(rs.getString("IDENTIFICACION"));
        auditoria.setIdAdmin(rs.getLong("ID_ADMIN"));
        auditoria.setCampoModificado(rs.getString("CAMPO_MODIFICADO"));
        auditoria.setValorAnterior(rs.getString("VALOR_ANTERIOR"));
        auditoria.setValorNuevo(rs.getString("VALOR_NUEVO"));
        auditoria.setMotivo(rs.getString("MOTIVO"));
        auditoria.setAccion(rs.getString("ACCION"));
        auditoria.setFechaHora(rs.getTimestamp("FECHA_HORA"));
        auditoria.setNombreAdmin(rs.getString("NOMBRE_ADMIN"));
        return auditoria;
    }

    private String selectBase() {
        return "SELECT av.ID_AUDITORIA, av.IDENTIFICACION, av.ID_ADMIN, av.CAMPO_MODIFICADO, " +
                "av.VALOR_ANTERIOR, av.VALOR_NUEVO, av.MOTIVO, av.ACCION, av.FECHA_HORA, " +
                "a.NOMBRE AS NOMBRE_ADMIN " +
                "FROM Auditoria_votantes av " +
                "JOIN Administradores a ON a.ID_ADMIN = av.ID_ADMIN";
    }

    private int normalizarLimite(int limite) {
        return Math.max(1, Math.min(50, limite));
    }
}
