package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Eleccion;
import com.abisupc.model.EstadoEleccion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EleccionRepository implements Repository<Eleccion> {

    @Override
    public List<Eleccion> findAll() {
        List<Eleccion> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection()) {
            String inicioCol = fechaInicioColumn(conn);
            String finCol = fechaFinColumn(conn);
            String sql = "SELECT ID_ELECCION, NOMBRE, " + inicioCol + " AS FECHAHORA_INICIO, " +
                    finCol + " AS FECHAHORA_FIN, ESTADO FROM Elecciones ORDER BY " + inicioCol + " DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en EleccionRepository.findAll", e);
        }
    }

    @Override
    public Optional<Eleccion> findById(Long id) {
        try (Connection conn = AppConfig.getConnection()) {
            String inicioCol = fechaInicioColumn(conn);
            String finCol = fechaFinColumn(conn);
            String sql = "SELECT ID_ELECCION, NOMBRE, " + inicioCol + " AS FECHAHORA_INICIO, " +
                    finCol + " AS FECHAHORA_FIN, ESTADO FROM Elecciones WHERE ID_ELECCION = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en EleccionRepository.findById - id: " + id, e);
        }
    }

    @Override
    public void save(Eleccion e) {
        try (Connection conn = AppConfig.getConnection()) {
            String sql = "INSERT INTO Elecciones (ID_ELECCION, NOMBRE, " + fechaInicioColumn(conn) + ", " +
                    fechaFinColumn(conn) + ", ESTADO) " +
                    "VALUES (seq_elecciones.NEXTVAL, ?, ?, ?, 'PROGRAMADA')";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, e.getNombre());
                ps.setTimestamp(2, Timestamp.valueOf(e.getFechaHoraInicio()));
                ps.setTimestamp(3, Timestamp.valueOf(e.getFechaHoraFin()));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT seq_elecciones.CURRVAL FROM dual");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    e.setId(rs.getLong(1));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.save", ex);
        }
    }

    @Override
    public void update(Eleccion e) {
        try (Connection conn = AppConfig.getConnection()) {
            String sql = "UPDATE Elecciones SET NOMBRE = ?, " + fechaInicioColumn(conn) + " = ?, " +
                    fechaFinColumn(conn) + " = ? WHERE ID_ELECCION = ? AND ESTADO = 'PROGRAMADA'";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, e.getNombre());
                ps.setTimestamp(2, Timestamp.valueOf(e.getFechaHoraInicio()));
                ps.setTimestamp(3, Timestamp.valueOf(e.getFechaHoraFin()));
                ps.setLong(4, e.getId());
                int filas = ps.executeUpdate();
                if (filas == 0) {
                    throw new IllegalStateException("Solo se puede editar una eleccion en estado PROGRAMADA");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.update - id: " + e.getId(), ex);
        }
    }

    public void cambiarEstado(Long id, String nuevoEstado) {
        String sql = "UPDATE Elecciones SET ESTADO = ? WHERE ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.cambiarEstado - id: " + id, ex);
        }
    }

    public boolean tieneVotos(Long id) {
        String sql = "SELECT COUNT(*) FROM Votos WHERE ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.tieneVotos - id: " + id, ex);
        }
    }

    public boolean hayEleccionEnCurso() {
        String sql = "SELECT COUNT(*) FROM Elecciones WHERE ESTADO = 'EN_CURSO'";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.hayEleccionEnCurso", ex);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM Elecciones WHERE ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.delete - id: " + id, ex);
        }
    }

    public Optional<Eleccion> findActiva() {
        return findByEstado("EN_CURSO").stream().findFirst();
    }

    public List<Eleccion> findByEstado(String estado) {
        List<Eleccion> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection()) {
            String inicioCol = fechaInicioColumn(conn);
            String finCol = fechaFinColumn(conn);
            String sql = "SELECT ID_ELECCION, NOMBRE, " + inicioCol + " AS FECHAHORA_INICIO, " +
                    finCol + " AS FECHAHORA_FIN, ESTADO FROM Elecciones WHERE ESTADO = ? ORDER BY " + inicioCol + " DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, estado);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(mapRow(rs));
                    }
                    return lista;
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.findByEstado - estado: " + estado, ex);
        }
    }

    public void actualizarEstado(Long idEleccion, String estado) {
        cambiarEstado(idEleccion, estado);
    }

    private Eleccion mapRow(ResultSet rs) throws SQLException {
        Eleccion eleccion = new Eleccion();
        Timestamp inicio = rs.getTimestamp("FECHAHORA_INICIO");
        Timestamp fin = rs.getTimestamp("FECHAHORA_FIN");

        eleccion.setId(rs.getLong("ID_ELECCION"));
        eleccion.setNombre(rs.getString("NOMBRE"));
        eleccion.setFechaHoraInicio(inicio != null ? inicio.toLocalDateTime() : null);
        eleccion.setFechaHoraFin(fin != null ? fin.toLocalDateTime() : null);
        eleccion.setEstado(EstadoEleccion.fromDb(rs.getString("ESTADO")));

        return eleccion;
    }

    private String fechaInicioColumn(Connection conn) throws SQLException {
        return columnExists(conn, "ELECCIONES", "FECHAHORA_INICIO") ? "FECHAHORA_INICIO" : "FECHA_HORA_INICIO";
    }

    private String fechaFinColumn(Connection conn) throws SQLException {
        return columnExists(conn, "ELECCIONES", "FECHAHORA_FIN") ? "FECHAHORA_FIN" : "FECHA_HORA_FIN";
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
