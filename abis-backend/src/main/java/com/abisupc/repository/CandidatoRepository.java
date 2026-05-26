package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Candidato;
import com.abisupc.model.CandidatoEleccion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CandidatoRepository implements Repository<Candidato> {

    @Override
    public List<Candidato> findAll() {
        String sql = "SELECT ID_CANDIDATO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, FOTO_URL FROM Candidatos ORDER BY ID_CANDIDATO";
        List<Candidato> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection()) {
            ensureFotoUrlColumn(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapCandidato(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.findAll", e);
        }
    }

    @Override
    public Optional<Candidato> findById(Long id) {
        String sql = "SELECT ID_CANDIDATO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, FOTO_URL FROM Candidatos WHERE ID_CANDIDATO = ?";
        try (Connection conn = AppConfig.getConnection()) {
            ensureFotoUrlColumn(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapCandidato(rs));
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.findById - id: " + id, e);
        }
    }

    @Override
    public void save(Candidato entity) {
        Long id = savePersona(entity);
        entity.setId(id);
    }

    public Long savePersona(Candidato c) {
        String sql = "INSERT INTO Candidatos (ID_CANDIDATO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, FOTO_URL) " +
                "VALUES (seq_candidatos.NEXTVAL, ?, ?, ?, ?, ?)";
        try (Connection conn = AppConfig.getConnection()) {
            ensureFotoUrlColumn(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, c.getPrimerNombre());
                ps.setString(2, c.getSegundoNombre());
                ps.setString(3, c.getPrimerApellido());
                ps.setString(4, c.getSegundoApellido());
                ps.setString(5, c.getFotoUrl());
                ps.executeUpdate();
            }
            try (PreparedStatement seq = conn.prepareStatement("SELECT seq_candidatos.CURRVAL FROM dual");
                 ResultSet rs = seq.executeQuery()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    c.setId(id);
                    return id;
                }
                throw new SQLException("No se pudo leer seq_candidatos.CURRVAL");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.savePersona", e);
        }
    }

    public void savePostulacion(Long idCandidato, Long idEleccion, Integer numeroCampania, String cargo) {
        validarNumeroCampaniaDisponible(idEleccion, numeroCampania, null);
        String sql = "INSERT INTO Candidatos_eleccion (ID_CANDIDATO, ID_ELECCION, NUMERO_CAMPANIA, CARGO) VALUES (?, ?, ?, ?)";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idCandidato);
            ps.setLong(2, idEleccion);
            ps.setInt(3, numeroCampania);
            ps.setString(4, cargo);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.savePostulacion", e);
        }
    }

    @Override
    public void update(Candidato entity) {
        String sql = "UPDATE Candidatos SET PRIMER_NOMBRE = ?, SEGUNDO_NOMBRE = ?, PRIMER_APELLIDO = ?, SEGUNDO_APELLIDO = ?, FOTO_URL = COALESCE(?, FOTO_URL) WHERE ID_CANDIDATO = ?";
        try (Connection conn = AppConfig.getConnection()) {
            ensureFotoUrlColumn(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, entity.getPrimerNombre());
                ps.setString(2, entity.getSegundoNombre());
                ps.setString(3, entity.getPrimerApellido());
                ps.setString(4, entity.getSegundoApellido());
                ps.setString(5, entity.getFotoUrl());
                ps.setLong(6, entity.getId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.update - id: " + entity.getId(), e);
        }
    }

    public void updatePostulacion(Long idCandidato, Long idEleccion, Integer numeroCampania, String cargo) {
        validarNumeroCampaniaDisponible(idEleccion, numeroCampania, idCandidato);
        String sql = "UPDATE Candidatos_eleccion SET NUMERO_CAMPANIA = ?, CARGO = ? WHERE ID_CANDIDATO = ? AND ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, numeroCampania);
            ps.setString(2, cargo);
            ps.setLong(3, idCandidato);
            ps.setLong(4, idEleccion);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.updatePostulacion", e);
        }
    }

    public List<CandidatoEleccion> findByEleccion(Long idEleccion) {
        List<CandidatoEleccion> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection()) {
            ensureFotoUrlColumn(conn);
            String pesoCol = pesoColumn(conn);
            String sql = "SELECT ce.ID_CANDIDATO, ce.ID_ELECCION, ce.NUMERO_CAMPANIA, ce.CARGO, " +
                    "c.PRIMER_NOMBRE, c.SEGUNDO_NOMBRE, c.PRIMER_APELLIDO, c.SEGUNDO_APELLIDO, c.FOTO_URL, " +
                    "NVL(v.TOTAL_VOTOS, 0) AS VOTOS " +
                    "FROM Candidatos_eleccion ce JOIN Candidatos c ON c.ID_CANDIDATO = ce.ID_CANDIDATO " +
                    "LEFT JOIN (SELECT ID_CANDIDATO, ID_ELECCION, SUM(" + pesoCol + ") AS TOTAL_VOTOS FROM Votos " +
                    "WHERE ID_ELECCION = ? GROUP BY ID_CANDIDATO, ID_ELECCION) v " +
                    "ON v.ID_CANDIDATO = ce.ID_CANDIDATO AND v.ID_ELECCION = ce.ID_ELECCION " +
                    "WHERE ce.ID_ELECCION = ? ORDER BY ce.CARGO, ce.NUMERO_CAMPANIA";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, idEleccion);
                ps.setLong(2, idEleccion);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(mapRow(rs));
                    }
                    return lista;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.findByEleccion - idEleccion: " + idEleccion, e);
        }
    }

    public boolean tieneVotos(Long idCandidato, Long idEleccion) {
        String sql = "SELECT COUNT(*) FROM Votos WHERE ID_CANDIDATO = ? AND ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idCandidato);
            ps.setLong(2, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.tieneVotos", e);
        }
    }

    public void deletePostulacion(Long idCandidato, Long idEleccion) {
        String sql = "DELETE FROM Candidatos_eleccion WHERE ID_CANDIDATO = ? AND ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idCandidato);
            ps.setLong(2, idEleccion);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.deletePostulacion", e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM Candidatos WHERE ID_CANDIDATO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.delete - id: " + id, e);
        }
    }

    private void validarNumeroCampaniaDisponible(Long idEleccion, Integer numeroCampania, Long idExcluir) {
        String sql = "SELECT COUNT(*) FROM Candidatos_eleccion WHERE ID_ELECCION = ? AND NUMERO_CAMPANIA = ?";
        if (idExcluir != null) {
            sql += " AND ID_CANDIDATO != ?";
        }

        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            ps.setInt(2, numeroCampania);
            if (idExcluir != null) {
                ps.setLong(3, idExcluir);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new IllegalArgumentException("Número de campaña ya existe en esta elección");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.validarNumeroCampaniaDisponible", e);
        }
    }

    private Candidato mapCandidato(ResultSet rs) throws SQLException {
        Candidato c = new Candidato();
        c.setId(rs.getLong("ID_CANDIDATO"));
        c.setPrimerNombre(rs.getString("PRIMER_NOMBRE"));
        c.setSegundoNombre(rs.getString("SEGUNDO_NOMBRE"));
        c.setPrimerApellido(rs.getString("PRIMER_APELLIDO"));
        c.setSegundoApellido(rs.getString("SEGUNDO_APELLIDO"));
        c.setFotoUrl(rs.getString("FOTO_URL"));
        return c;
    }

    private CandidatoEleccion mapRow(ResultSet rs) throws SQLException {
        CandidatoEleccion ce = new CandidatoEleccion();
        ce.setIdCandidato(rs.getLong("ID_CANDIDATO"));
        ce.setIdEleccion(rs.getLong("ID_ELECCION"));
        ce.setNumeroCampania(rs.getInt("NUMERO_CAMPANIA"));
        ce.setCargo(rs.getString("CARGO"));
        ce.setPrimerNombre(rs.getString("PRIMER_NOMBRE"));
        ce.setSegundoNombre(rs.getString("SEGUNDO_NOMBRE"));
        ce.setPrimerApellido(rs.getString("PRIMER_APELLIDO"));
        ce.setSegundoApellido(rs.getString("SEGUNDO_APELLIDO"));
        ce.setFotoUrl(rs.getString("FOTO_URL"));
        ce.setVotos(rs.getDouble("VOTOS"));
        return ce;
    }

    private void ensureFotoUrlColumn(Connection conn) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'CANDIDATOS' AND COLUMN_NAME = 'FOTO_URL'";
        try (PreparedStatement ps = conn.prepareStatement(checkSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("ALTER TABLE Candidatos ADD FOTO_URL VARCHAR2(500)")) {
            ps.executeUpdate();
        }
    }

    private static String pesoColumn(Connection conn) throws SQLException {
        return columnExists(conn, "VOTOS", "PESO_VOTO_APLICADO") ? "PESO_VOTO_APLICADO" : "PESOVOTO_APLICADO";
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
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
