package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Voto;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VotoRepository implements Repository<Voto> {

    private Voto mapRow(ResultSet rs) throws SQLException {
        Voto voto = new Voto();

        voto.setId(rs.getLong("ID_VOTO"));
        voto.setIdEleccion(rs.getLong("ID_ELECCION"));
        Long idCandidato = rs.getLong("ID_CANDIDATO");
        voto.setIdCandidato(rs.wasNull() ? null : idCandidato);
        voto.setFechaHora(rs.getTimestamp("FECHA_HORA"));
        voto.setPesoVotoAplicado(rs.getDouble("PESO_VOTO_APLICADO"));

        return voto;
    }

    @Override
    public Optional<Voto> findById(Long id) {
        String sql = "SELECT ID_VOTO, FECHA_HORA, PESO_VOTO_APLICADO, ID_ELECCION, ID_CANDIDATO " +
                "FROM Votos WHERE ID_VOTO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.findById - id: " + id, e);
        }
    }

    @Override
    public List<Voto> findAll() {
        String sql = "SELECT ID_VOTO, FECHA_HORA, PESO_VOTO_APLICADO, ID_ELECCION, ID_CANDIDATO " +
                "FROM Votos ORDER BY ID_VOTO";
        List<Voto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapRow(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.findAll", e);
        }
    }

    @Override
    public void save(Voto entity) {
        String sql = "INSERT INTO Votos (ID_VOTO, FECHA_HORA, PESO_VOTO_APLICADO, ID_ELECCION, ID_CANDIDATO) " +
                "VALUES (seq_votos.NEXTVAL, ?, ?, ?, ?) RETURNING ID_VOTO INTO ?";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setTimestamp(1, entity.getFechaHora() != null ? entity.getFechaHora() : new Timestamp(System.currentTimeMillis()));
            cs.setDouble(2, entity.getPesoVotoAplicado());
            cs.setLong(3, entity.getIdEleccion());
            setNullableLong(cs, 4, entity.getIdCandidato());
            cs.registerOutParameter(5, Types.NUMERIC);
            cs.execute();
            entity.setId(cs.getLong(5));
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.save", e);
        }
    }

    @Override
    public void update(Voto entity) {
        String sql = "UPDATE Votos SET FECHA_HORA = ?, PESO_VOTO_APLICADO = ?, ID_ELECCION = ?, ID_CANDIDATO = ? " +
                "WHERE ID_VOTO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, entity.getFechaHora());
            ps.setDouble(2, entity.getPesoVotoAplicado());
            ps.setLong(3, entity.getIdEleccion());
            setNullableLong(ps, 4, entity.getIdCandidato());
            ps.setLong(5, entity.getId());

            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontro el voto con ID: " + entity.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.update - id: " + entity.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM Votos WHERE ID_VOTO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontro el voto con ID: " + id);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.delete - id: " + id, e);
        }
    }

    public List<Voto> findByEleccion(Long idEleccion) {
        String sql = "SELECT ID_VOTO, FECHA_HORA, PESO_VOTO_APLICADO, ID_ELECCION, ID_CANDIDATO " +
                "FROM Votos WHERE ID_ELECCION = ? ORDER BY FECHA_HORA DESC";
        List<Voto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.findByEleccion - idEleccion: " + idEleccion, e);
        }
    }

    public int countByCandidato(Long idCandidato) {
        String sql = idCandidato == null
                ? "SELECT COUNT(*) FROM Votos WHERE ID_CANDIDATO IS NULL"
                : "SELECT COUNT(*) FROM Votos WHERE ID_CANDIDATO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (idCandidato != null) {
                ps.setLong(1, idCandidato);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.countByCandidato - idCandidato: " + idCandidato, e);
        }
    }

    public Map<Long, Integer> obtenerResultados(Long idEleccion) {
        String sql = "SELECT ID_CANDIDATO, COUNT(*) AS TOTAL_VOTOS FROM Votos " +
                "WHERE ID_ELECCION = ? GROUP BY ID_CANDIDATO";
        Map<Long, Integer> resultados = new HashMap<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Long idCandidato = rs.getLong("ID_CANDIDATO");
                    resultados.put(rs.wasNull() ? null : idCandidato, rs.getInt("TOTAL_VOTOS"));
                }
                return resultados;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.obtenerResultados - idEleccion: " + idEleccion, e);
        }
    }

    public Map<Long, Double> obtenerResultadosPonderados(Long idEleccion) {
        String sql = "SELECT ID_CANDIDATO, SUM(PESO_VOTO_APLICADO) AS TOTAL_PONDERADO FROM Votos " +
                "WHERE ID_ELECCION = ? GROUP BY ID_CANDIDATO";
        Map<Long, Double> resultados = new HashMap<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Long idCandidato = rs.getLong("ID_CANDIDATO");
                    resultados.put(rs.wasNull() ? null : idCandidato, rs.getDouble("TOTAL_PONDERADO"));
                }
                return resultados;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.obtenerResultadosPonderados - idEleccion: " + idEleccion, e);
        }
    }

    public Map<String, Integer> obtenerResultadosPorRol(Long idEleccion) {
        return new HashMap<>();
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setLong(index, value);
        }
    }
}
