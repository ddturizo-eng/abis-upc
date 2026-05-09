package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Candidato;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CandidatoRepository implements Repository<Candidato> {

    private Candidato mapRow(ResultSet rs) throws SQLException {
        Candidato candidato = new Candidato();

        candidato.setId(rs.getLong("ID_CANDIDATO"));
        candidato.setPrimerNombre(rs.getString("PRIMER_NOMBRE"));
        candidato.setSegundoNombre(rs.getString("SEGUNDO_NOMBRE"));
        candidato.setPrimerApellido(rs.getString("PRIMER_APELLIDO"));
        candidato.setSegundoApellido(rs.getString("SEGUNDO_APELLIDO"));
        candidato.setNumeroCampania(String.valueOf(rs.getInt("NUMERO_CAMPANIA")));
        candidato.setCargo(rs.getString("CARGO"));
        candidato.setIdEleccion(rs.getLong("ELECCIONES_IDELECCION"));

        return candidato;
    }

    @Override
    public Optional<Candidato> findById(Long id) {
        String sql = "SELECT ID_CANDIDATO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, " +
                "SEGUNDO_APELLIDO, NUMERO_CAMPANIA, CARGO, ELECCIONES_IDELECCION " +
                "FROM CANDIDATOS WHERE ID_CANDIDATO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.findById - id: " + id, e);
        }
    }

    @Override
    public List<Candidato> findAll() {
        String sql = "SELECT ID_CANDIDATO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, " +
                "SEGUNDO_APELLIDO, NUMERO_CAMPANIA, CARGO, ELECCIONES_IDELECCION " +
                "FROM CANDIDATOS ORDER BY ID_CANDIDATO";
        List<Candidato> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.findAll", e);
        }
    }

    @Override
    public void save(Candidato entity) {
        String sql = "INSERT INTO CANDIDATOS (ID_CANDIDATO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, " +
                "SEGUNDO_APELLIDO, NUMERO_CAMPANIA, CARGO, ELECCIONES_IDELECCION) " +
                "VALUES (SEQ_CANDIDATOS.NEXTVAL, ?, ?, ?, ?, ?, ?, ?) RETURNING ID_CANDIDATO INTO ?";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setString(1, entity.getPrimerNombre());
            cs.setString(2, entity.getSegundoNombre());
            cs.setString(3, entity.getPrimerApellido());
            cs.setString(4, entity.getSegundoApellido());
            cs.setInt(5, Integer.parseInt(entity.getNumeroCampania()));
            cs.setString(6, entity.getCargo());
            cs.setLong(7, entity.getIdEleccion());
            cs.registerOutParameter(8, Types.NUMERIC);
            cs.execute();
            entity.setId(cs.getLong(8));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1) {
                throw new RuntimeException("Ya existe un candidato con el mismo número de campaña en esa elección.", e);
            }
            throw new RuntimeException("Error en CandidatoRepository.save", e);
        } catch (NumberFormatException e) {
            throw new RuntimeException("NUMERO_CAMPANIA debe ser numérico para guardar el candidato.", e);
        }
    }

    @Override
    public void update(Candidato entity) {
        String sql = "UPDATE CANDIDATOS SET PRIMER_NOMBRE = ?, SEGUNDO_NOMBRE = ?, PRIMER_APELLIDO = ?, " +
                "SEGUNDO_APELLIDO = ?, NUMERO_CAMPANIA = ?, CARGO = ?, ELECCIONES_IDELECCION = ? " +
                "WHERE ID_CANDIDATO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getPrimerNombre());
            ps.setString(2, entity.getSegundoNombre());
            ps.setString(3, entity.getPrimerApellido());
            ps.setString(4, entity.getSegundoApellido());
            ps.setInt(5, Integer.parseInt(entity.getNumeroCampania()));
            ps.setString(6, entity.getCargo());
            ps.setLong(7, entity.getIdEleccion());
            ps.setLong(8, entity.getId());

            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el candidato con ID: " + entity.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.update - id: " + entity.getId(), e);
        } catch (NumberFormatException e) {
            throw new RuntimeException("NUMERO_CAMPANIA debe ser numérico para actualizar el candidato.", e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM CANDIDATOS WHERE ID_CANDIDATO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el candidato con ID: " + id);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 2292) {
                throw new RuntimeException("No se puede eliminar el candidato ID " + id + " porque tiene votos asociados.", e);
            }
            throw new RuntimeException("Error en CandidatoRepository.delete - id: " + id, e);
        }
    }

    public List<Candidato> findByEleccion(Long idEleccion) {
        String sql = "SELECT ID_CANDIDATO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, " +
                "SEGUNDO_APELLIDO, NUMERO_CAMPANIA, CARGO, ELECCIONES_IDELECCION " +
                "FROM CANDIDATOS WHERE ELECCIONES_IDELECCION = ? ORDER BY CARGO, NUMERO_CAMPANIA";
        List<Candidato> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.findByEleccion - idEleccion: " + idEleccion, e);
        }
    }

    public List<Candidato> findByCargo(Long idEleccion, String cargo) {
        String sql = "SELECT ID_CANDIDATO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, " +
                "SEGUNDO_APELLIDO, NUMERO_CAMPANIA, CARGO, ELECCIONES_IDELECCION " +
                "FROM CANDIDATOS WHERE ELECCIONES_IDELECCION = ? AND CARGO = ? " +
                "ORDER BY NUMERO_CAMPANIA";
        List<Candidato> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            ps.setString(2, cargo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.findByCargo - idEleccion: " + idEleccion + ", cargo: " + cargo, e);
        }
    }

    public List<String> getCargosDistintos(Long idEleccion) {
        String sql = "SELECT DISTINCT CARGO FROM CANDIDATOS WHERE ELECCIONES_IDELECCION = ? ORDER BY CARGO";
        List<String> cargos = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) cargos.add(rs.getString("CARGO"));
                return cargos;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.getCargosDistintos - idEleccion: " + idEleccion, e);
        }
    }
}
