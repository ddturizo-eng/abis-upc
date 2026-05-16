package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.BiometriaVotante;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class BiometriaVotanteRepository {

    public Optional<BiometriaVotante> findByIdentificacion(String identificacion) {
        String sql = "SELECT * FROM (" +
                "SELECT * FROM Biometria_votantes WHERE IDENTIFICACION = ? AND ACTIVO = 'S' " +
                "ORDER BY ID_BIOMETRIA DESC) WHERE ROWNUM = 1";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en BiometriaVotanteRepository.findByIdentificacion", e);
        }
    }

    public void save(BiometriaVotante b) {
        String sql = "INSERT INTO Biometria_votantes (ID_BIOMETRIA, IDENTIFICACION, PLANTILLA_BIOMETRICA, " +
                "HASHINTEGRIDADBIOMETRICA, FECHA_ENROLAMIENTO, ACTIVO) " +
                "VALUES (seq_biometria_votantes.NEXTVAL, ?, ?, ?, SYSDATE, 'S')";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, b.getIdentificacion());
            ps.setBytes(2, b.getPlantillaBiometrica());
            ps.setString(3, b.getHashIntegridadBiometrica());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en BiometriaVotanteRepository.save", e);
        }
    }

    public void desactivar(String identificacion) {
        String sql = "UPDATE Biometria_votantes SET ACTIVO = 'N' WHERE IDENTIFICACION = ? AND ACTIVO = 'S'";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en BiometriaVotanteRepository.desactivar", e);
        }
    }

    public void anonimizar(String identificacion) {
        String sql = "DELETE FROM Biometria_votantes WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en BiometriaVotanteRepository.anonimizar", e);
        }
    }

    private BiometriaVotante mapRow(ResultSet rs) throws SQLException {
        BiometriaVotante b = new BiometriaVotante();
        b.setIdBiometria(rs.getLong("ID_BIOMETRIA"));
        b.setIdentificacion(rs.getString("IDENTIFICACION"));
        b.setPlantillaBiometrica(rs.getBytes("PLANTILLA_BIOMETRICA"));
        b.setHashIntegridadBiometrica(rs.getString("HASHINTEGRIDADBIOMETRICA"));
        b.setFechaEnrolamiento(rs.getTimestamp("FECHA_ENROLAMIENTO"));
        b.setActivo(rs.getString("ACTIVO"));
        return b;
    }
}
