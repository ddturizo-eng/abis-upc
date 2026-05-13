package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.PuestoVotacion;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PuestoVotacionRepository implements Repository<PuestoVotacion> {

    private PuestoVotacion mapRow(ResultSet rs) throws SQLException {
        PuestoVotacion p = new PuestoVotacion();
        p.setId(rs.getLong("ID_PUESTO"));
        p.setCiudad(rs.getString("CIUDAD"));
        p.setSede(rs.getString("SEDE"));
        p.setNombrePuesto(rs.getString("NOMBRE_PUESTO"));
        return p;
    }

    @Override
    public Optional<PuestoVotacion> findById(Long id) {
        String sql = "SELECT ID_PUESTO, CIUDAD, SEDE, NOMBRE_PUESTO " +
                "FROM Puestos_votacion WHERE ID_PUESTO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en PuestoVotacionRepository.findById - id: " + id, e);
        }
    }

    @Override
    public List<PuestoVotacion> findAll() {
        String sql = "SELECT ID_PUESTO, CIUDAD, SEDE, NOMBRE_PUESTO " +
                "FROM Puestos_votacion ORDER BY ID_PUESTO";
        List<PuestoVotacion> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapRow(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en PuestoVotacionRepository.findAll", e);
        }
    }

    @Override
    public void save(PuestoVotacion entity) {
        String sql = "INSERT INTO Puestos_votacion (ID_PUESTO, CIUDAD, SEDE, NOMBRE_PUESTO) " +
                "VALUES (seq_puestos_votacion.NEXTVAL, ?, ?, ?)";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getCiudad());
            ps.setString(2, entity.getSede());
            ps.setString(3, entity.getNombrePuesto());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en PuestoVotacionRepository.save", e);
        }
    }

    @Override
    public void update(PuestoVotacion entity) {
        String sql = "UPDATE Puestos_votacion SET CIUDAD = ?, SEDE = ?, NOMBRE_PUESTO = ? WHERE ID_PUESTO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getCiudad());
            ps.setString(2, entity.getSede());
            ps.setString(3, entity.getNombrePuesto());
            ps.setLong(4, entity.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en PuestoVotacionRepository.update - id: " + entity.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM Puestos_votacion WHERE ID_PUESTO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en PuestoVotacionRepository.delete - id: " + id, e);
        }
    }
}
