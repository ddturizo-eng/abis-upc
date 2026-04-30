package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Sesion;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SesionRepository implements Repository<Sesion> {

    private Sesion mapRow(ResultSet rs) throws SQLException {
        Sesion s = new Sesion();
        s.setId(rs.getLong("ID_SESION"));
        s.setToken(rs.getString("TOKEN"));
        Timestamp fechaInicio = rs.getTimestamp("FECHA_INICIO");
        if (fechaInicio != null) s.setFechaInicio(fechaInicio.toLocalDateTime());
        Timestamp fechaFin = rs.getTimestamp("FECHA_FIN");
        if (fechaFin != null) s.setFechaFin(fechaFin.toLocalDateTime());
        s.setIdAdministrador(rs.getLong("ADMINISTRADORES_IDADMIN"));
        return s;
    }

    @Override
    public Optional<Sesion> findById(Long id) {
        String sql = "SELECT ID_SESION, TOKEN, FECHA_INICIO, FECHA_FIN, ADMINISTRADORES_IDADMIN " +
                "FROM SESIONES WHERE ID_SESION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.findById — id: " + id, e);
        }
    }

    @Override
    public List<Sesion> findAll() {
        String sql = "SELECT ID_SESION, TOKEN, FECHA_INICIO, FECHA_FIN, ADMINISTRADORES_IDADMIN " +
                "FROM SESIONES ORDER BY ID_SESION";
        List<Sesion> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.findAll", e);
        }
    }

    @Override
    public void save(Sesion entity) {
        String sql = "INSERT INTO SESIONES (ID_SESION, TOKEN, FECHA_INICIO, ADMINISTRADORES_IDADMIN) " +
                "VALUES (SEQ_SESIONES.NEXTVAL, ?, SYSTIMESTAMP, ?) RETURNING ID_SESION INTO ?";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setString(1, entity.getToken());
            cs.setLong(2, entity.getIdAdministrador());
            cs.registerOutParameter(3, Types.NUMERIC);
            cs.execute();
            entity.setId(cs.getLong(3));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1)
                throw new RuntimeException("Ya existe una sesión con el token proporcionado.", e);
            if (e.getErrorCode() == 2291)
                throw new RuntimeException("No existe el administrador con ID: " + entity.getIdAdministrador(), e);
            throw new RuntimeException("Error en SesionRepository.save", e);
        }
    }

    @Override
    public void update(Sesion entity) {
        String sql = "UPDATE SESIONES SET TOKEN = ?, FECHA_FIN = ?, ADMINISTRADORES_IDADMIN = ? " +
                "WHERE ID_SESION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getToken());
            ps.setTimestamp(2, entity.getFechaFin() != null ? Timestamp.valueOf(entity.getFechaFin()) : null);
            ps.setLong(3, entity.getIdAdministrador());
            ps.setLong(4, entity.getId());
            int filas = ps.executeUpdate();
            if (filas == 0)
                throw new RuntimeException("No se encontró la sesión con ID: " + entity.getId());
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.update — id: " + entity.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM SESIONES WHERE ID_SESION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0)
                throw new RuntimeException("No se encontró la sesión con ID: " + id);
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.delete — id: " + id, e);
        }
    }

    public Optional<Sesion> findByToken(String token) {
        String sql = "SELECT ID_SESION, TOKEN, FECHA_INICIO, FECHA_FIN, ADMINISTRADORES_IDADMIN " +
                "FROM SESIONES WHERE TOKEN = ? AND FECHA_FIN IS NULL";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.findByToken", e);
        }
    }

    public Optional<Sesion> findActivaByAdmin(Long idAdmin) {
        String sql = "SELECT ID_SESION, TOKEN, FECHA_INICIO, FECHA_FIN, ADMINISTRADORES_IDADMIN " +
                "FROM SESIONES WHERE ADMINISTRADORES_IDADMIN = ? AND FECHA_FIN IS NULL";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idAdmin);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.findActivaByAdmin — idAdmin: " + idAdmin, e);
        }
    }

    public void invalidarToken(String token) {
        String sql = "UPDATE SESIONES SET FECHA_FIN = SYSTIMESTAMP WHERE TOKEN = ? AND FECHA_FIN IS NULL";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            int filas = ps.executeUpdate();
            if (filas == 0)
                throw new RuntimeException("No se encontró sesión activa con ese token.");
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.invalidarToken", e);
        }
    }

    public void limpiarSesionesExpiradas() {
        String sql = "DELETE FROM SESIONES WHERE FECHA_FIN IS NOT NULL " +
                "AND FECHA_FIN < SYSTIMESTAMP - INTERVAL '30' DAY";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.limpiarSesionesExpiradas", e);
        }
    }
}