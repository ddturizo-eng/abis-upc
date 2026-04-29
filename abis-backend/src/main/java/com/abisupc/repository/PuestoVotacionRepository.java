package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.PuestoVotacion;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PuestoVotacionRepository implements Repository<PuestoVotacion> {

    private PuestoVotacion mapRow(ResultSet rs) throws SQLException {
        PuestoVotacion p = new PuestoVotacion();
        p.setId(rs.getLong("ID_PUESTOS"));
        p.setCiudad(rs.getString("CIUDAD"));
        p.setSede(rs.getString("SEDE"));
        p.setNombrePuesto(rs.getString("NOMBRE_PUESTO"));
        return p;
    }

    @Override
    public Optional<PuestoVotacion> findById(Long id) {
        String sql = "SELECT ID_PUESTOS, CIUDAD, SEDE, NOMBRE_PUESTO FROM PUESTOS_VOTACION WHERE ID_PUESTOS = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error en PuestoVotacionRepository.findById — id: " + id, e);
        }
    }

    @Override
    public List<PuestoVotacion> findAll() {
        String sql = "SELECT ID_PUESTOS, CIUDAD, SEDE, NOMBRE_PUESTO FROM PUESTOS_VOTACION ORDER BY ID_PUESTOS";
        List<PuestoVotacion> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en PuestoVotacionRepository.findAll", e);
        }
    }

    @Override
    public void save(PuestoVotacion entity) {
        String sql = "INSERT INTO PUESTOS_VOTACION (ID_PUESTOS, CIUDAD, SEDE, NOMBRE_PUESTO) " +
                "VALUES (SEQ_PUESTOS_VOTACION.NEXTVAL, ?, ?, ?) RETURNING ID_PUESTOS INTO ?";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setString(1, entity.getCiudad());
            cs.setString(2, entity.getSede());
            cs.setString(3, entity.getNombrePuesto());
            cs.registerOutParameter(4, Types.NUMERIC);
            cs.execute();
            entity.setId(cs.getLong(4));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1)
                throw new RuntimeException("Ya existe un puesto con ese nombre: " + entity.getNombrePuesto(), e);
            throw new RuntimeException("Error en PuestoVotacionRepository.save", e);
        }
    }

    @Override
    public void update(PuestoVotacion entity) {
        String sql = "UPDATE PUESTOS_VOTACION SET CIUDAD = ?, SEDE = ?, NOMBRE_PUESTO = ? WHERE ID_PUESTOS = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getCiudad());
            ps.setString(2, entity.getSede());
            ps.setString(3, entity.getNombrePuesto());
            ps.setLong(4, entity.getId());
            int filas = ps.executeUpdate();
            if (filas == 0)
                throw new RuntimeException("No se encontró el puesto con ID: " + entity.getId());
        } catch (SQLException e) {
            throw new RuntimeException("Error en PuestoVotacionRepository.update — id: " + entity.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM PUESTOS_VOTACION WHERE ID_PUESTOS = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0)
                throw new RuntimeException("No se encontró el puesto con ID: " + id);
        } catch (SQLException e) {
            if (e.getErrorCode() == 2292)
                throw new RuntimeException("No se puede eliminar el puesto ID " + id + " porque tiene votantes o mesas asociadas.", e);
            throw new RuntimeException("Error en PuestoVotacionRepository.delete — id: " + id, e);
        }
    }
}