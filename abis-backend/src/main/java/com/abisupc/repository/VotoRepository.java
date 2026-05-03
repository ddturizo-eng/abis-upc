package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Voto;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VotoRepository implements Repository<Voto> {

    private Voto mapRow(ResultSet rs) throws SQLException {
        Voto voto = new Voto();

        voto.setId(rs.getLong("ID_VOTO"));
        voto.setIdRol(rs.getLong("ROLES_IDROL"));
        voto.setIdEleccion(rs.getLong("ELECCIONES_IDELECCION"));
        voto.setIdCandidato(rs.getLong("IDCANDIDATO"));
        voto.setFechaHora(rs.getTimestamp("FECHA_HORA"));
        voto.setPesoVotoAplicado(rs.getDouble("PESOVOTO_APLICADO"));

        return voto;
    }

    @Override
    public Optional<Voto> findById(Long id) {
        String sql = "SELECT ID_VOTO, ROLES_IDROL, ELECCIONES_IDELECCION, IDCANDIDATO, FECHA_HORA, PESOVOTO_APLICADO " +
                "FROM VOTOS WHERE ID_VOTO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.findById - id: " + id, e);
        }
    }

    @Override
    public List<Voto> findAll() {
        String sql = "SELECT ID_VOTO, ROLES_IDROL, ELECCIONES_IDELECCION, IDCANDIDATO, FECHA_HORA, PESOVOTO_APLICADO " +
                "FROM VOTOS ORDER BY ID_VOTO";
        List<Voto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.findAll", e);
        }
    }

    @Override
    public void save(Voto entity) {
        String sql = "INSERT INTO VOTOS (ID_VOTO, ROLES_IDROL, ELECCIONES_IDELECCION, IDCANDIDATO, FECHA_HORA, PESOVOTO_APLICADO) " +
                "VALUES (SEQ_VOTOS.NEXTVAL, ?, ?, ?, ?, ?) RETURNING ID_VOTO INTO ?";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setLong(1, entity.getIdRol());
            cs.setLong(2, entity.getIdEleccion());
            cs.setLong(3, entity.getIdCandidato());
            cs.setTimestamp(4, entity.getFechaHora());
            cs.setDouble(5, entity.getPesoVotoAplicado());
            cs.registerOutParameter(6, Types.NUMERIC);
            cs.execute();
            entity.setId(cs.getLong(6));
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.save", e);
        }
    }

    @Override
    public void update(Voto entity) {
        String sql = "UPDATE VOTOS SET ROLES_IDROL = ?, ELECCIONES_IDELECCION = ?, IDCANDIDATO = ?, " +
                "FECHA_HORA = ?, PESOVOTO_APLICADO = ? WHERE ID_VOTO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, entity.getIdRol());
            ps.setLong(2, entity.getIdEleccion());
            ps.setLong(3, entity.getIdCandidato());
            ps.setTimestamp(4, entity.getFechaHora());
            ps.setDouble(5, entity.getPesoVotoAplicado());
            ps.setLong(6, entity.getId());

            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el voto con ID: " + entity.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.update - id: " + entity.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM VOTOS WHERE ID_VOTO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el voto con ID: " + id);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.delete - id: " + id, e);
        }
    }

    public List<Voto> findByEleccion(Long idEleccion) {
        String sql = "SELECT ID_VOTO, ROLES_IDROL, ELECCIONES_IDELECCION, IDCANDIDATO, FECHA_HORA, PESOVOTO_APLICADO " +
                "FROM VOTOS WHERE ELECCIONES_IDELECCION = ? ORDER BY FECHA_HORA DESC";
        List<Voto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.findByEleccion - idEleccion: " + idEleccion, e);
        }
    }

    public int countByCandidato(Long idCandidato) {
        String sql = "SELECT COUNT(*) FROM VOTOS WHERE IDCANDIDATO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idCandidato);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.countByCandidato - idCandidato: " + idCandidato, e);
        }
    }

    public Map<Long, Integer> obtenerResultados(Long idEleccion) {
        String sql = "SELECT IDCANDIDATO, COUNT(*) AS TOTAL_VOTOS FROM VOTOS " +
                "WHERE ELECCIONES_IDELECCION = ? GROUP BY IDCANDIDATO";
        Map<Long, Integer> resultados = new HashMap<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultados.put(rs.getLong("IDCANDIDATO"), rs.getInt("TOTAL_VOTOS"));
                }
                return resultados;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.obtenerResultados - idEleccion: " + idEleccion, e);
        }
    }

    public Map<Long, Double> obtenerResultadosPonderados(Long idEleccion) {
        String sql = "SELECT IDCANDIDATO, SUM(PESOVOTO_APLICADO) AS TOTAL_PONDERADO FROM VOTOS " +
                "WHERE ELECCIONES_IDELECCION = ? GROUP BY IDCANDIDATO";
        Map<Long, Double> resultados = new HashMap<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultados.put(rs.getLong("IDCANDIDATO"), rs.getDouble("TOTAL_PONDERADO"));
                }
                return resultados;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.obtenerResultadosPonderados - idEleccion: " + idEleccion, e);
        }
    }

    public Map<String, Integer> obtenerResultadosPorRol(Long idEleccion) {
        String sql = "SELECT ROLES_IDROL, COUNT(*) AS TOTAL_VOTOS FROM VOTOS " +
                "WHERE ELECCIONES_IDELECCION = ? GROUP BY ROLES_IDROL";
        Map<String, Integer> resultados = new HashMap<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultados.put(String.valueOf(rs.getLong("ROLES_IDROL")), rs.getInt("TOTAL_VOTOS"));
                }
                return resultados;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.obtenerResultadosPorRol - idEleccion: " + idEleccion, e);
        }
    }
}
