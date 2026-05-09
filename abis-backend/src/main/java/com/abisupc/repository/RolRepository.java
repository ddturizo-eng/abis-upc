package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Rol;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RolRepository implements Repository<Rol> {

    private Rol mapRow(ResultSet rs) throws SQLException {
        Rol rol = new Rol();
        rol.setId(rs.getLong("ID_ROL"));
        rol.setNombre(rs.getString("NOMBRE"));
        rol.setPesoVoto(rs.getDouble("PESO_VOTO"));
        return rol;
    }

    @Override
    public Optional<Rol> findById(Long id) {
        String sql = "SELECT ID_ROL, NOMBRE, PESO_VOTO FROM ROLES WHERE ID_ROL = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RolRepository.findById — id: " + id, e);
        }
    }

    @Override
    public List<Rol> findAll() {
        String sql = "SELECT ID_ROL, NOMBRE, PESO_VOTO FROM ROLES ORDER BY ID_ROL";
        List<Rol> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en RolRepository.findAll", e);
        }
    }

    @Override
    public void save(Rol entity) {
        String sql = "INSERT INTO ROLES (ID_ROL, NOMBRE, PESO_VOTO) " +
                "VALUES (SEQ_ROLES.NEXTVAL, ?, ?) RETURNING ID_ROL INTO ?";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setString(1, entity.getNombre());
            cs.setDouble(2, entity.getPesoVoto());
            cs.registerOutParameter(3, Types.NUMERIC);
            cs.execute();
            entity.setId(cs.getLong(3));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1)
                throw new RuntimeException("Ya existe un rol con el nombre: " + entity.getNombre(), e);
            throw new RuntimeException("Error en RolRepository.save", e);
        }
    }

    @Override
    public void update(Rol entity) {
        String sql = "UPDATE ROLES SET NOMBRE = ?, PESO_VOTO = ? WHERE ID_ROL = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getNombre());
            ps.setDouble(2, entity.getPesoVoto());
            ps.setLong(3, entity.getId());
            int filas = ps.executeUpdate();
            if (filas == 0)
                throw new RuntimeException("No se encontró el rol con ID: " + entity.getId());
        } catch (SQLException e) {
            throw new RuntimeException("Error en RolRepository.update — id: " + entity.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM ROLES WHERE ID_ROL = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0)
                throw new RuntimeException("No se encontró el rol con ID: " + id);
        } catch (SQLException e) {
            if (e.getErrorCode() == 2292)
                throw new RuntimeException("No se puede eliminar el rol ID " + id + " porque tiene votantes asignados.", e);
            throw new RuntimeException("Error en RolRepository.delete — id: " + id, e);
        }
    }

    public Optional<Rol> findByNombre(String nombre) {
        String sql = "SELECT ID_ROL, NOMBRE, PESO_VOTO FROM ROLES WHERE NOMBRE = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RolRepository.findByNombre — nombre: " + nombre, e);
        }
    }

    public boolean estaEnUso(Long idRol) {
        String sql = "SELECT COUNT(*) FROM VOTANTES WHERE ROLES_IDROL = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idRol);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RolRepository.estaEnUso — idRol: " + idRol, e);
        }
    }

    public double getPesoVoto(Long idRol) {
        String sql = "SELECT PESO_VOTO FROM ROLES WHERE ID_ROL = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idRol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("PESO_VOTO");
                throw new RuntimeException("No existe el rol con ID: " + idRol +
                        ". Inconsistencia entre VOTANTES.ROLES_IDROL y tabla ROLES.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RolRepository.getPesoVoto — idRol: " + idRol, e);
        }
    }
}