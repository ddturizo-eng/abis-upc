package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.RegistroVoto;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RegistroVotoRepository implements Repository<RegistroVoto> {

    private RegistroVoto mapRow(ResultSet rs) throws SQLException {
        RegistroVoto registro = new RegistroVoto();

        Timestamp fechaHora = rs.getTimestamp("FECHA_HORA");

        registro.setId(rs.getLong("ID_REGISTRO"));
        registro.setFechaHora(fechaHora != null ? fechaHora.toLocalDateTime() : null);
        registro.setIdentificacion(rs.getString("IDENTIFICACION"));
        registro.setIdPuesto(rs.getLong("ID_PUESTO"));
        registro.setIdEleccion(rs.getLong("ID_ELECCION"));

        return registro;
    }

    @Override
    public Optional<RegistroVoto> findById(Long id) {
        String sql = "SELECT ID_REGISTRO, FECHA_HORA, IDENTIFICACION, ID_PUESTO, ID_ELECCION " +
                "FROM Registro_votos WHERE ID_REGISTRO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.findById - id: " + id, e);
        }
    }

    @Override
    public List<RegistroVoto> findAll() {
        String sql = "SELECT ID_REGISTRO, FECHA_HORA, IDENTIFICACION, ID_PUESTO, ID_ELECCION " +
                "FROM Registro_votos ORDER BY ID_REGISTRO";
        List<RegistroVoto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.findAll", e);
        }
    }

    @Override
    public void save(RegistroVoto entity) {
        String sql = "INSERT INTO Registro_votos (ID_REGISTRO, FECHA_HORA, IDENTIFICACION, ID_PUESTO, ID_ELECCION) " +
                "VALUES (SEQ_REGISTRO_VOTOS.NEXTVAL, ?, ?, ?, ?) RETURNING ID_REGISTRO INTO ?";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setTimestamp(1, entity.getFechaHora() != null ? Timestamp.valueOf(entity.getFechaHora()) : null);
            cs.setString(2, entity.getIdentificacion());
            cs.setLong(3, entity.getIdPuesto());
            cs.setLong(4, entity.getIdEleccion());
            cs.registerOutParameter(5, Types.NUMERIC);
            cs.execute();
            entity.setId(cs.getLong(5));
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.save", e);
        }
    }

    @Override
    public void update(RegistroVoto entity) {
        String sql = "UPDATE Registro_votos SET FECHA_HORA = ?, IDENTIFICACION = ?, ID_PUESTO = ?, ID_ELECCION = ? " +
                "WHERE ID_REGISTRO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, entity.getFechaHora() != null ? Timestamp.valueOf(entity.getFechaHora()) : null);
            ps.setString(2, entity.getIdentificacion());
            ps.setLong(3, entity.getIdPuesto());
            ps.setLong(4, entity.getIdEleccion());
            ps.setLong(5, entity.getId());

            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el registro de voto con ID: " + entity.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.update - id: " + entity.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM Registro_votos WHERE ID_REGISTRO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el registro de voto con ID: " + id);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.delete - id: " + id, e);
        }
    }

    public boolean yaVoto(String identificacion, Long idEleccion) {
        String sql = "SELECT COUNT(*) FROM Registro_votos " +
                "WHERE IDENTIFICACION = ? AND ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ps.setLong(2, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.yaVoto - identificacion: " +
                    identificacion + ", idEleccion: " + idEleccion, e);
        }
    }

    public List<RegistroVoto> findByEleccion(Long idEleccion) {
        String sql = "SELECT ID_REGISTRO, FECHA_HORA, IDENTIFICACION, ID_PUESTO, ID_ELECCION " +
                "FROM Registro_votos WHERE ID_ELECCION = ? ORDER BY FECHA_HORA DESC";
        List<RegistroVoto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.findByEleccion - idEleccion: " + idEleccion, e);
        }
    }

    public List<RegistroVoto> findByIdentificacion(String identificacion) {
        String sql = "SELECT ID_REGISTRO, FECHA_HORA, IDENTIFICACION, ID_PUESTO, ID_ELECCION " +
                "FROM Registro_votos WHERE IDENTIFICACION = ? ORDER BY FECHA_HORA DESC";
        List<RegistroVoto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.findByIdentificacion - identificacion: " + identificacion, e);
        }
    }

    public int countByPuesto(Long idPuesto, Long idEleccion) {
        String sql = "SELECT COUNT(*) FROM Registro_votos " +
                "WHERE ID_PUESTO = ? AND ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idPuesto);
            ps.setLong(2, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.countByPuesto - idPuesto: " +
                    idPuesto + ", idEleccion: " + idEleccion, e);
        }
    }
}
