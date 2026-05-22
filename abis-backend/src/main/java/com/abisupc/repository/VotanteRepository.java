package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.EstadoVotante;
import com.abisupc.model.Votante;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VotanteRepository implements Repository<Votante> {

    private static final String SELECT_BASE = "SELECT IDENTIFICACION, CORREO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, " +
            "PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, FECHA_CONSENTIMIENTO, ID_ROL, ID_PUESTO, QR_CEDULA FROM Votantes";

    private Votante mapRow(ResultSet rs) throws SQLException {
        Votante votante = new Votante();
        votante.setIdentificacion(rs.getString("IDENTIFICACION"));
        votante.setCorreo(rs.getString("CORREO"));
        votante.setPrimerNombre(rs.getString("PRIMER_NOMBRE"));
        votante.setSegundoNombre(rs.getString("SEGUNDO_NOMBRE"));
        votante.setPrimerApellido(rs.getString("PRIMER_APELLIDO"));
        votante.setSegundoApellido(rs.getString("SEGUNDO_APELLIDO"));
        votante.setEstadoVoto(rs.getString("ESTADO_VOTO"));
        votante.setFotoUrl(rs.getString("FOTO_URL"));
        votante.setFechaConsentimiento(rs.getTimestamp("FECHA_CONSENTIMIENTO"));
        votante.setIdRol(rs.getLong("ID_ROL"));
        votante.setIdPuesto(rs.getLong("ID_PUESTO"));
        votante.setQrCedula(rs.getString("QR_CEDULA"));
        return votante;
    }

    @Override
    public Optional<Votante> findById(Long id) {
        return findByIdentificacion(String.valueOf(id));
    }

    @Override
    public List<Votante> findAll() {
        String sql = SELECT_BASE + " ORDER BY PRIMER_APELLIDO, PRIMER_NOMBRE";
        List<Votante> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapRow(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findAll", e);
        }
    }

    @Override
    public void save(Votante entity) {
        String sql = "INSERT INTO Votantes (IDENTIFICACION, CORREO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, " +
                "PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, FECHA_CONSENTIMIENTO, ID_ROL, ID_PUESTO, QR_CEDULA) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getIdentificacion());
            ps.setString(2, entity.getCorreo());
            ps.setString(3, entity.getPrimerNombre());
            ps.setString(4, entity.getSegundoNombre());
            ps.setString(5, entity.getPrimerApellido());
            ps.setString(6, entity.getSegundoApellido());
            ps.setString(7, entity.getEstadoVoto() != null ? entity.getEstadoVoto() : EstadoVotante.PENDIENTE.name());
            ps.setString(8, entity.getFotoUrl());
            ps.setTimestamp(9, entity.getFechaConsentimiento());
            ps.setLong(10, entity.getIdRol());
            ps.setLong(11, entity.getIdPuesto());
            ps.setString(12, entity.getQrCedula());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1) {
                throw new RuntimeException("Ya existe un votante con la identificacion: " + entity.getIdentificacion(), e);
            }
            throw new RuntimeException("Error en VotanteRepository.save", e);
        }
    }

    @Override
    public void update(Votante entity) {
        String sql = "UPDATE Votantes SET CORREO = ?, PRIMER_NOMBRE = ?, SEGUNDO_NOMBRE = ?, " +
                "PRIMER_APELLIDO = ?, SEGUNDO_APELLIDO = ?, FOTO_URL = ?, " +
                "FECHA_CONSENTIMIENTO = ?, ID_ROL = ?, ID_PUESTO = ?, QR_CEDULA = ? WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getCorreo());
            ps.setString(2, entity.getPrimerNombre());
            ps.setString(3, entity.getSegundoNombre());
            ps.setString(4, entity.getPrimerApellido());
            ps.setString(5, entity.getSegundoApellido());
            ps.setString(6, entity.getFotoUrl());
            ps.setTimestamp(7, entity.getFechaConsentimiento());
            ps.setLong(8, entity.getIdRol());
            ps.setLong(9, entity.getIdPuesto());
            ps.setString(10, entity.getQrCedula());
            ps.setString(11, entity.getIdentificacion());
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontro el votante con identificacion: " + entity.getIdentificacion());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.update - identificacion: " + entity.getIdentificacion(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM Votantes WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(id));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.delete - identificacion: " + id, e);
        }
    }

    public Optional<Votante> findByIdentificacion(String identificacion) {
        String sql = SELECT_BASE + " WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByIdentificacion - identificacion: " + identificacion, e);
        }
    }

    public Optional<Votante> findByCorreo(String correo) {
        String sql = SELECT_BASE + " WHERE CORREO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, correo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByCorreo - correo: " + correo, e);
        }
    }

    public Optional<Votante> findByQrCedula(String qrCedula) {
        String sql = SELECT_BASE + " WHERE QR_CEDULA = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, qrCedula);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByQrCedula", e);
        }
    }

    public List<Votante> findByIdRol(Long idRol) {
        String sql = SELECT_BASE + " WHERE ID_ROL = ? ORDER BY PRIMER_APELLIDO, PRIMER_NOMBRE";
        List<Votante> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idRol);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByIdRol - idRol: " + idRol, e);
        }
    }

    public List<Votante> findByIdPuesto(Long idPuesto) {
        String sql = SELECT_BASE + " WHERE ID_PUESTO = ? ORDER BY PRIMER_APELLIDO, PRIMER_NOMBRE";
        List<Votante> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idPuesto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByIdPuesto - idPuesto: " + idPuesto, e);
        }
    }

    public List<Votante> findHabilitadosParaContingencia() {
        String sql = SELECT_BASE + " WHERE UPPER(ESTADO_VOTO) = 'PENDIENTE' AND CORREO IS NOT NULL " +
                "ORDER BY PRIMER_APELLIDO, PRIMER_NOMBRE";
        List<Votante> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapRow(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error consultando votantes habilitados para contingencia", e);
        }
    }

    public boolean estaHabilitado(String identificacion) {
        String sql = "SELECT ESTADO_VOTO FROM Votantes WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && EstadoVotante.PENDIENTE.name().equals(rs.getString("ESTADO_VOTO"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.estaHabilitado - identificacion: " + identificacion, e);
        }
    }

    public void actualizarEstado(String identificacion, String estado) {
        throw new UnsupportedOperationException("El estado del votante se cambia mediante procedimientos Oracle autorizados");
    }

    public void actualizarFoto(String identificacion, String fotoUrl) {
        String sql = "UPDATE Votantes SET FOTO_URL = ? WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fotoUrl);
            ps.setString(2, identificacion);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontro el votante con identificacion: " + identificacion);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.actualizarFoto - identificacion: " + identificacion, e);
        }
    }
}
