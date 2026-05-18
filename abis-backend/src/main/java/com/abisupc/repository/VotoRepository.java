package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Voto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VotoRepository implements Repository<Voto> {

    private Voto mapRow(ResultSet rs) throws SQLException {
        Voto voto = new Voto();

        voto.setId(rs.getLong("ID_VOTO_ALIAS"));
        voto.setIdEleccion(rs.getLong("ID_ELECCION"));
        Long idCandidato = rs.getLong("ID_CANDIDATO");
        voto.setIdCandidato(rs.wasNull() ? null : idCandidato);
        voto.setFechaHora(rs.getTimestamp("FECHA_HORA"));
        voto.setPesoVotoAplicado(rs.getDouble("PESO_VOTO_ALIAS"));

        return voto;
    }

    @Override
    public Optional<Voto> findById(Long id) {
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectBase(conn) + " WHERE " + idColumn(conn) + " = ?")) {
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
        List<Voto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectBase(conn) + " ORDER BY " + idColumn(conn));
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
        throw new UnsupportedOperationException("Los votos solo se registran mediante prc_registrar_voto");
    }

    @Override
    public void update(Voto entity) {
        throw new UnsupportedOperationException("Los votos son inmutables y no se actualizan desde Java");
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("Los votos son inmutables y no se eliminan desde Java");
    }

    public List<Voto> findByEleccion(Long idEleccion) {
        List<Voto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectBase(conn) + " WHERE ID_ELECCION = ? ORDER BY FECHA_HORA DESC")) {
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
        Map<Long, Double> resultados = new HashMap<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT ID_CANDIDATO, SUM(" + pesoColumn(conn) + ") AS TOTAL_PONDERADO FROM Votos " +
                     "WHERE ID_ELECCION = ? GROUP BY ID_CANDIDATO")) {
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

    private String selectBase(Connection conn) throws SQLException {
        return "SELECT " + idColumn(conn) + " AS ID_VOTO_ALIAS, FECHA_HORA, " +
                pesoColumn(conn) + " AS PESO_VOTO_ALIAS, ID_ELECCION, ID_CANDIDATO FROM Votos";
    }

    private String idColumn(Connection conn) throws SQLException {
        return columnExists(conn, "ID_VOTO") ? "ID_VOTO" : "ID_VOTOS";
    }

    private String pesoColumn(Connection conn) throws SQLException {
        return columnExists(conn, "PESO_VOTO_APLICADO") ? "PESO_VOTO_APLICADO" : "PESOVOTO_APLICADO";
    }

    private boolean columnExists(Connection conn, String columnName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'VOTOS' AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

}
