package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Sesion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
        s.setIdAdministrador(rs.getLong("ID_ADMINISTRADOR"));
        return s;
    }

    @Override
    public Optional<Sesion> findById(Long id) {
        try (Connection conn = AppConfig.getConnection()) {
            String sql = selectBase(conn) + " WHERE ID_SESION = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapRow(rs));
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.findById - id: " + id, e);
        }
    }

    @Override
    public List<Sesion> findAll() {
        List<Sesion> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection()) {
            String sql = selectBase(conn) + " ORDER BY ID_SESION";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.findAll", e);
        }
    }

    @Override
    public void save(Sesion entity) {
        try (Connection conn = AppConfig.getConnection()) {
            Long nextId;
            try (PreparedStatement psSeq = conn.prepareStatement("SELECT SEQ_SESIONES.NEXTVAL FROM DUAL");
                 ResultSet rsSeq = psSeq.executeQuery()) {
                if (rsSeq.next()) {
                    nextId = rsSeq.getLong(1);
                } else {
                    throw new RuntimeException("No se pudo obtener el siguiente ID de la secuencia");
                }
            }
            entity.setId(nextId);

            String adminColumn = adminColumn(conn);
            String sql = "INSERT INTO SESIONES (ID_SESION, TOKEN, FECHA_INICIO, " + adminColumn + ") " +
                    "VALUES (?, ?, SYSTIMESTAMP, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, entity.getId());
                ps.setString(2, entity.getToken());
                ps.setLong(3, entity.getIdAdministrador());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1)
                throw new RuntimeException("Ya existe una sesion con el token proporcionado.", e);
            if (e.getErrorCode() == 2291)
                throw new RuntimeException("No existe el administrador con ID: " + entity.getIdAdministrador(), e);
            throw new RuntimeException("Error en SesionRepository.save", e);
        }
    }

    @Override
    public void update(Sesion entity) {
        try (Connection conn = AppConfig.getConnection()) {
            String adminColumn = adminColumn(conn);
            String sql = "UPDATE SESIONES SET TOKEN = ?, FECHA_FIN = ?, " + adminColumn + " = ? " +
                    "WHERE ID_SESION = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, entity.getToken());
                ps.setTimestamp(2, entity.getFechaFin() != null ? Timestamp.valueOf(entity.getFechaFin()) : null);
                ps.setLong(3, entity.getIdAdministrador());
                ps.setLong(4, entity.getId());
                int filas = ps.executeUpdate();
                if (filas == 0)
                    throw new RuntimeException("No se encontro la sesion con ID: " + entity.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.update - id: " + entity.getId(), e);
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
                throw new RuntimeException("No se encontro la sesion con ID: " + id);
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.delete - id: " + id, e);
        }
    }

    public Optional<Sesion> findByToken(String token) {
        try (Connection conn = AppConfig.getConnection()) {
            String sql = selectBase(conn) + " WHERE TOKEN = ? AND FECHA_FIN IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, token);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapRow(rs));
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.findByToken", e);
        }
    }

    public Optional<Sesion> findActivaByAdmin(Long idAdmin) {
        try (Connection conn = AppConfig.getConnection()) {
            String adminColumn = adminColumn(conn);
            String sql = selectBase(conn) + " WHERE " + adminColumn + " = ? AND FECHA_FIN IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, idAdmin);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapRow(rs));
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en SesionRepository.findActivaByAdmin - idAdmin: " + idAdmin, e);
        }
    }

    public void invalidarToken(String token) {
        String sql = "UPDATE SESIONES SET FECHA_FIN = SYSTIMESTAMP WHERE TOKEN = ? AND FECHA_FIN IS NULL";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            int filas = ps.executeUpdate();
            if (filas == 0)
                throw new RuntimeException("No se encontro sesion activa con ese token.");
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

    private String selectBase(Connection conn) throws SQLException {
        String adminColumn = adminColumn(conn);
        return "SELECT ID_SESION, TOKEN, FECHA_INICIO, FECHA_FIN, " +
                adminColumn + " AS ID_ADMINISTRADOR FROM SESIONES";
    }

    private String adminColumn(Connection conn) throws SQLException {
        return columnExists(conn, "SESIONES", "ID_ADMIN") ? "ID_ADMIN" : "ADMINISTRADORES_IDADMIN";
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
