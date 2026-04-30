package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Administrador;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdministradorRepository implements Repository<Administrador> {

    private Administrador mapRow(ResultSet rs) throws SQLException {
        Administrador a = new Administrador();
        a.setId(rs.getLong("ID_ADMIN"));
        a.setUsuario(rs.getString("USUARIO"));
        a.setPasswordHash(rs.getString("PASSWORD_HASH"));
        a.setNombre(rs.getString("NOMBRE"));
        a.setCorreo(rs.getString("CORREO"));
        return a;
    }

    @Override
    public Optional<Administrador> findById(Long id) {
        String sql = "SELECT ID_ADMIN, USUARIO, PASSWORD_HASH, NOMBRE, CORREO " +
                "FROM ADMINISTRADORES WHERE ID_ADMIN = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error en AdministradorRepository.findById — id: " + id, e);
        }
    }

    @Override
    public List<Administrador> findAll() {
        String sql = "SELECT ID_ADMIN, USUARIO, PASSWORD_HASH, NOMBRE, CORREO " +
                "FROM ADMINISTRADORES ORDER BY ID_ADMIN";
        List<Administrador> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en AdministradorRepository.findAll", e);
        }
    }

    @Override
    public void save(Administrador entity) {
        String sql = "INSERT INTO ADMINISTRADORES (ID_ADMIN, USUARIO, PASSWORD_HASH, NOMBRE, CORREO) " +
                "VALUES (SEQ_ADMINISTRADORES.NEXTVAL, ?, ?, ?, ?) RETURNING ID_ADMIN INTO ?";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setString(1, entity.getUsuario());
            cs.setString(2, entity.getPasswordHash());
            cs.setString(3, entity.getNombre());
            cs.setString(4, entity.getCorreo());
            cs.registerOutParameter(5, Types.NUMERIC);
            cs.execute();
            entity.setId(cs.getLong(5));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1)
                throw new RuntimeException("Ya existe un administrador con el usuario: " + entity.getUsuario(), e);
            throw new RuntimeException("Error en AdministradorRepository.save", e);
        }
    }

    @Override
    public void update(Administrador entity) {
        String sql = "UPDATE ADMINISTRADORES SET USUARIO = ?, PASSWORD_HASH = ?, NOMBRE = ?, CORREO = ? " +
                "WHERE ID_ADMIN = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getUsuario());
            ps.setString(2, entity.getPasswordHash());
            ps.setString(3, entity.getNombre());
            ps.setString(4, entity.getCorreo());
            ps.setLong(5, entity.getId());
            int filas = ps.executeUpdate();
            if (filas == 0)
                throw new RuntimeException("No se encontró el administrador con ID: " + entity.getId());
        } catch (SQLException e) {
            throw new RuntimeException("Error en AdministradorRepository.update — id: " + entity.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM ADMINISTRADORES WHERE ID_ADMIN = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0)
                throw new RuntimeException("No se encontró el administrador con ID: " + id);
        } catch (SQLException e) {
            if (e.getErrorCode() == 2292)
                throw new RuntimeException("No se puede eliminar el administrador ID " + id + " porque tiene sesiones asociadas.", e);
            throw new RuntimeException("Error en AdministradorRepository.delete — id: " + id, e);
        }
    }

    public Optional<Administrador> findByUsuario(String usuario) {
        String sql = "SELECT ID_ADMIN, USUARIO, PASSWORD_HASH, NOMBRE, CORREO " +
                "FROM ADMINISTRADORES WHERE USUARIO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error en AdministradorRepository.findByUsuario — usuario: " + usuario, e);
        }
    }

    public Optional<Administrador> findByCorreo(String correo) {
        String sql = "SELECT ID_ADMIN, USUARIO, PASSWORD_HASH, NOMBRE, CORREO " +
                "FROM ADMINISTRADORES WHERE CORREO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, correo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error en AdministradorRepository.findByCorreo — correo: " + correo, e);
        }
    }
}