package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Eleccion;
import com.abisupc.model.EstadoEleccion;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EleccionRepository implements Repository<Eleccion> {

    private Eleccion mapRow(ResultSet rs) throws SQLException {
        Eleccion eleccion = new Eleccion();

        Timestamp inicio = rs.getTimestamp("FECHA_HORA_INICIO");
        Timestamp fin = rs.getTimestamp("FECHA_HORA_FIN");

        eleccion.setId(rs.getLong("ID_ELECCION"));
        eleccion.setNombre(rs.getString("NOMBRE"));
        eleccion.setFechaHoraInicio(inicio != null ? inicio.toLocalDateTime() : null);
        eleccion.setFechaHoraFin(fin != null ? fin.toLocalDateTime() : null);
        eleccion.setEstado(EstadoEleccion.valueOf(rs.getString("ESTADO")));

        return eleccion;
    }

    @Override
    public Optional<Eleccion> findById(Long id) {
        String sql = "SELECT ID_ELECCION, NOMBRE, FECHA_HORA_INICIO, FECHA_HORA_FIN, ESTADO " +
                "FROM ELECCIONES WHERE ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error en EleccionRepository.findById - id: " + id, e);
        }
    }

    @Override
    public List<Eleccion> findAll() {
        String sql = "SELECT ID_ELECCION, NOMBRE, FECHA_HORA_INICIO, FECHA_HORA_FIN, ESTADO " +
                "FROM ELECCIONES ORDER BY ID_ELECCION";
        List<Eleccion> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en EleccionRepository.findAll", e);
        }
    }

    @Override
    public void save(Eleccion entity) {
        String sql = "INSERT INTO ELECCIONES (ID_ELECCION, NOMBRE, FECHA_HORA_INICIO, FECHA_HORA_FIN, ESTADO) " +
                "VALUES (SEQ_ELECCIONES.NEXTVAL, ?, ?, ?, ?) RETURNING ID_ELECCION INTO ?";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setString(1, entity.getNombre());
            cs.setTimestamp(2, entity.getFechaHoraInicio() != null ? Timestamp.valueOf(entity.getFechaHoraInicio()) : null);
            cs.setTimestamp(3, entity.getFechaHoraFin() != null ? Timestamp.valueOf(entity.getFechaHoraFin()) : null);
            cs.setString(4, entity.getEstado().name());
            cs.registerOutParameter(5, Types.NUMERIC);
            cs.execute();
            entity.setId(cs.getLong(5));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1) {
                throw new RuntimeException("Ya existe una eleccion con el nombre: " + entity.getNombre(), e);
            }
            throw new RuntimeException("Error en EleccionRepository.save", e);
        }
    }

    @Override
    public void update(Eleccion entity) {
        String sql = "UPDATE ELECCIONES SET NOMBRE = ?, FECHA_HORA_INICIO = ?, FECHA_HORA_FIN = ?, ESTADO = ? " +
                "WHERE ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getNombre());
            ps.setTimestamp(2, entity.getFechaHoraInicio() != null ? Timestamp.valueOf(entity.getFechaHoraInicio()) : null);
            ps.setTimestamp(3, entity.getFechaHoraFin() != null ? Timestamp.valueOf(entity.getFechaHoraFin()) : null);
            ps.setString(4, entity.getEstado().name());
            ps.setLong(5, entity.getId());

            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró la elección con ID: " + entity.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en EleccionRepository.update - id: " + entity.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM ELECCIONES WHERE ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró la elección con ID: " + id);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 2292) {
                throw new RuntimeException("No se puede eliminar la elección ID " + id + " porque tiene registros asociados.", e);
            }
            throw new RuntimeException("Error en EleccionRepository.delete - id: " + id, e);
        }
    }

    public Optional<Eleccion> findActiva() {
        String sql = "SELECT ID_ELECCION, NOMBRE, FECHA_HORA_INICIO, FECHA_HORA_FIN, ESTADO " +
                "FROM ELECCIONES WHERE ESTADO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, EstadoEleccion.EN_CURSO.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error en EleccionRepository.findActiva", e);
        }
    }

    public List<Eleccion> findByEstado(String estado) {
        String sql = "SELECT ID_ELECCION, NOMBRE, FECHA_HORA_INICIO, FECHA_HORA_FIN, ESTADO " +
                "FROM ELECCIONES WHERE ESTADO = ? ORDER BY FECHA_HORA_INICIO DESC";
        List<Eleccion> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en EleccionRepository.findByEstado - estado: " + estado, e);
        }
    }

    public void actualizarEstado(Long idEleccion, String estado) {
        String sql = "UPDATE ELECCIONES SET ESTADO = ? WHERE ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado);
            ps.setLong(2, idEleccion);

            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró la elección con ID: " + idEleccion);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en EleccionRepository.actualizarEstado - idEleccion: " + idEleccion, e);
        }
    }
}
