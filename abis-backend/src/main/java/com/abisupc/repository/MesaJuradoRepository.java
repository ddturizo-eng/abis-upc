package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.MesaJurado;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MesaJuradoRepository implements Repository<MesaJurado> {

    private MesaJurado mapRow(ResultSet rs) throws SQLException {
        MesaJurado m = new MesaJurado();
        m.setId(rs.getLong("ID_MESA"));
        Timestamp horaIngreso = rs.getTimestamp("HORA_INGRESO");
        if (horaIngreso != null) m.setHoraIngreso(horaIngreso.toLocalDateTime());
        Timestamp horaSalida = rs.getTimestamp("HORA_SALIDA");
        if (horaSalida != null) m.setHoraSalida(horaSalida.toLocalDateTime());
        m.setCargo(rs.getString("CARGO"));
        m.setIdPuesto(rs.getLong("ID_PUESTO"));
        return m;
    }

    @Override
    public Optional<MesaJurado> findById(Long id) {
        String sql = "SELECT ID_MESA, HORA_INGRESO, HORA_SALIDA, CARGO, ID_PUESTO " +
                "FROM MESA_JURADOS WHERE ID_MESA = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en MesaJuradoRepository.findById — id: " + id, e);
        }
    }

    @Override
    public List<MesaJurado> findAll() {
        String sql = "SELECT ID_MESA, HORA_INGRESO, HORA_SALIDA, CARGO, ID_PUESTO " +
                "FROM MESA_JURADOS ORDER BY ID_MESA";
        List<MesaJurado> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en MesaJuradoRepository.findAll", e);
        }
    }

    @Override
    public void save(MesaJurado entity) {
        String seqSql = "SELECT seq_mesa_jurados.NEXTVAL FROM dual";
        String sql = "INSERT INTO MESA_JURADOS (ID_MESA, HORA_INGRESO, HORA_SALIDA, CARGO, ID_PUESTO) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement seq = conn.prepareStatement(seqSql);
             ResultSet rs = seq.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("No se pudo leer seq_mesa_jurados.NEXTVAL");
            }
            long id = rs.getLong(1);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                ps.setTimestamp(2, Timestamp.valueOf(entity.getHoraIngreso()));
                ps.setTimestamp(3, entity.getHoraSalida() != null ? Timestamp.valueOf(entity.getHoraSalida()) : null);
                ps.setString(4, entity.getCargo());
                ps.setLong(5, entity.getIdPuesto());
                ps.executeUpdate();
                entity.setId(id);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 2291)
                throw new RuntimeException("No existe el puesto con ID: " + entity.getIdPuesto(), e);
            throw new RuntimeException("Error en MesaJuradoRepository.save", e);
        }
    }

    @Override
    public void update(MesaJurado entity) {
        String sql = "UPDATE MESA_JURADOS SET HORA_INGRESO = ?, HORA_SALIDA = ?, CARGO = ?, " +
                "ID_PUESTO = ? WHERE ID_MESA = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(entity.getHoraIngreso()));
            ps.setTimestamp(2, entity.getHoraSalida() != null ? Timestamp.valueOf(entity.getHoraSalida()) : null);
            ps.setString(3, entity.getCargo());
            ps.setLong(4, entity.getIdPuesto());
            ps.setLong(5, entity.getId());
            int filas = ps.executeUpdate();
            if (filas == 0)
                throw new RuntimeException("No se encontró la mesa con ID: " + entity.getId());
        } catch (SQLException e) {
            throw new RuntimeException("Error en MesaJuradoRepository.update — id: " + entity.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM MESA_JURADOS WHERE ID_MESA = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0)
                throw new RuntimeException("No se encontró la mesa con ID: " + id);
        } catch (SQLException e) {
            if (e.getErrorCode() == 2292)
                throw new RuntimeException("No se puede eliminar la mesa ID " + id + " porque tiene jurados asociados.", e);
            throw new RuntimeException("Error en MesaJuradoRepository.delete — id: " + id, e);
        }
    }

    public List<MesaJurado> findByPuesto(Long idPuesto) {
        String sql = "SELECT ID_MESA, HORA_INGRESO, HORA_SALIDA, CARGO, ID_PUESTO " +
                "FROM MESA_JURADOS WHERE ID_PUESTO = ?";
        List<MesaJurado> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idPuesto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en MesaJuradoRepository.findByPuesto — idPuesto: " + idPuesto, e);
        }
    }

    public List<MesaJurado> findActivas() {
        String sql = "SELECT ID_MESA, HORA_INGRESO, HORA_SALIDA, CARGO, ID_PUESTO " +
                "FROM MESA_JURADOS WHERE HORA_SALIDA IS NULL";
        List<MesaJurado> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en MesaJuradoRepository.findActivas", e);
        }
    }
}
