package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Rol;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RolRepository implements Repository<Rol> {

    private Rol mapRow(ResultSet rs) throws SQLException {
        Rol rol = new Rol();
        rol.setId(rs.getLong("ID_ROL"));
        rol.setNombre(rs.getString("NOMBRE"));
        return rol;
    }

    @Override
    public Optional<Rol> findById(Long id) {
        String sql = "SELECT ID_ROL, NOMBRE FROM Roles WHERE ID_ROL = ?";
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
            throw new RuntimeException("Error en RolRepository.findById - id: " + id, e);
        }
    }

    @Override
    public List<Rol> findAll() {
        String sql = "SELECT ID_ROL, NOMBRE FROM Roles ORDER BY ID_ROL";
        List<Rol> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapRow(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en RolRepository.findAll", e);
        }
    }

    @Override
    public void save(Rol entity) {
        String sql = "INSERT INTO Roles (ID_ROL, NOMBRE) VALUES (seq_roles.NEXTVAL, ?) RETURNING ID_ROL INTO ?";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setString(1, entity.getNombre());
            cs.registerOutParameter(2, Types.NUMERIC);
            cs.execute();
            entity.setId(cs.getLong(2));
        } catch (SQLException e) {
            throw new RuntimeException("Error en RolRepository.save", e);
        }
    }

    @Override
    public void update(Rol entity) {
        String sql = "UPDATE Roles SET NOMBRE = ? WHERE ID_ROL = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getNombre());
            ps.setLong(2, entity.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en RolRepository.update - id: " + entity.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM Roles WHERE ID_ROL = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en RolRepository.delete - id: " + id, e);
        }
    }

    public Optional<Rol> findByNombre(String nombre) {
        String sql = "SELECT ID_ROL, NOMBRE FROM Roles WHERE NOMBRE = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RolRepository.findByNombre - nombre: " + nombre, e);
        }
    }

    public boolean estaEnUso(Long idRol) {
        String sql = "SELECT COUNT(*) FROM Votantes WHERE ID_ROL = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idRol);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RolRepository.estaEnUso - idRol: " + idRol, e);
        }
    }
}
