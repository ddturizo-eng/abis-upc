package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.EstadoVotante;
import com.abisupc.model.Votante;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VotanteRepository implements Repository<Votante> {

    private Votante mapRow(ResultSet rs) throws SQLException {
        Votante votante = new Votante();

        votante.setId(rs.getLong("ID_VOTANTE"));
        votante.setIdentificacion(rs.getString("IDENTIFICACION"));
        votante.setPlantillaBiometrica(rs.getString("PLANTILLA_BIOMETRICA"));
        votante.setCorreo(rs.getString("CORREO"));
        votante.setPrimerNombre(rs.getString("PRIMER_NOMBRE"));
        votante.setSegundoNombre(rs.getString("SEGUNDO_NOMBRE"));
        votante.setPrimerApellido(rs.getString("PRIMER_APELLIDO"));
        votante.setSegundoApellido(rs.getString("SEGUNDO_APELLIDO"));
        votante.setEstadoVoto(rs.getString("ESTADO_VOTO"));
        votante.setFotoUrl(rs.getString("FOTO_URL"));
        votante.setFechaConsentimiento(rs.getTimestamp("FECHA_CONSENTIMIENTO"));
        votante.setHashIntegridadBiometrica(rs.getString("HASHINTEGRIDADBIOMETRICA"));
        votante.setIdRol(rs.getLong("ROLES_IDROL"));
        votante.setIdPuesto(rs.getLong("PUESTOS_VOTACION_IDPUESTOS"));

        return votante;
    }

    @Override
    public Optional<Votante> findById(Long id) {
        String sql = "SELECT ID_VOTANTE, IDENTIFICACION, PLANTILLA_BIOMETRICA, CORREO, PRIMER_NOMBRE, " +
                "SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, " +
                "FECHA_CONSENTIMIENTO, HASHINTEGRIDADBIOMETRICA, ROLES_IDROL, PUESTOS_VOTACION_IDPUESTOS " +
                "FROM VOTANTES WHERE ID_VOTANTE = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findById - id: " + id, e);
        }
    }

    @Override
    public List<Votante> findAll() {
        String sql = "SELECT ID_VOTANTE, IDENTIFICACION, PLANTILLA_BIOMETRICA, CORREO, PRIMER_NOMBRE, " +
                "SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, " +
                "FECHA_CONSENTIMIENTO, HASHINTEGRIDADBIOMETRICA, ROLES_IDROL, PUESTOS_VOTACION_IDPUESTOS " +
                "FROM VOTANTES ORDER BY ID_VOTANTE";
        List<Votante> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findAll", e);
        }
    }

    @Override
    public void save(Votante entity) {
        String sql = "INSERT INTO VOTANTES (ID_VOTANTE, IDENTIFICACION, PLANTILLA_BIOMETRICA, CORREO, PRIMER_NOMBRE, " +
                "SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, FECHA_CONSENTIMIENTO, " +
                "HASHINTEGRIDADBIOMETRICA, ROLES_IDROL, PUESTOS_VOTACION_IDPUESTOS) " +
                "VALUES (SEQ_VOTANTES.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING ID_VOTANTE INTO ?";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setString(1, entity.getIdentificacion());
            cs.setString(2, entity.getPlantillaBiometrica());
            cs.setString(3, entity.getCorreo());
            cs.setString(4, entity.getPrimerNombre());
            cs.setString(5, entity.getSegundoNombre());
            cs.setString(6, entity.getPrimerApellido());
            cs.setString(7, entity.getSegundoApellido());
            cs.setString(8, entity.getEstadoVoto());
            cs.setString(9, entity.getFotoUrl());
            cs.setTimestamp(10, entity.getFechaConsentimiento());
            cs.setString(11, entity.getHashIntegridadBiometrica());
            cs.setLong(12, entity.getIdRol());
            cs.setLong(13, entity.getIdPuesto());
            cs.registerOutParameter(14, Types.NUMERIC);
            cs.execute();
            entity.setId(cs.getLong(14));
        } catch (SQLException e) {
            if (e.getErrorCode() == 1) {
                throw new RuntimeException("Ya existe un votante con la identificación: " + entity.getIdentificacion(), e);
            }
            throw new RuntimeException("Error en VotanteRepository.save", e);
        }
    }

    @Override
    public void update(Votante entity) {
        String sql = "UPDATE VOTANTES SET IDENTIFICACION = ?, PLANTILLA_BIOMETRICA = ?, CORREO = ?, PRIMER_NOMBRE = ?, " +
                "SEGUNDO_NOMBRE = ?, PRIMER_APELLIDO = ?, SEGUNDO_APELLIDO = ?, ESTADO_VOTO = ?, FOTO_URL = ?, " +
                "FECHA_CONSENTIMIENTO = ?, HASHINTEGRIDADBIOMETRICA = ?, ROLES_IDROL = ?, PUESTOS_VOTACION_IDPUESTOS = ? " +
                "WHERE ID_VOTANTE = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getIdentificacion());
            ps.setString(2, entity.getPlantillaBiometrica());
            ps.setString(3, entity.getCorreo());
            ps.setString(4, entity.getPrimerNombre());
            ps.setString(5, entity.getSegundoNombre());
            ps.setString(6, entity.getPrimerApellido());
            ps.setString(7, entity.getSegundoApellido());
            ps.setString(8, entity.getEstadoVoto());
            ps.setString(9, entity.getFotoUrl());
            ps.setTimestamp(10, entity.getFechaConsentimiento());
            ps.setString(11, entity.getHashIntegridadBiometrica());
            ps.setLong(12, entity.getIdRol());
            ps.setLong(13, entity.getIdPuesto());
            ps.setLong(14, entity.getId());

            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el votante con ID: " + entity.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.update - id: " + entity.getId(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM VOTANTES WHERE ID_VOTANTE = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el votante con ID: " + id);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 2292) {
                throw new RuntimeException("No se puede eliminar el votante ID " + id + " porque tiene registros asociados.", e);
            }
            throw new RuntimeException("Error en VotanteRepository.delete - id: " + id, e);
        }
    }

    public Optional<Votante> findByIdentificacion(String identificacion) {
        String sql = "SELECT ID_VOTANTE, IDENTIFICACION, PLANTILLA_BIOMETRICA, CORREO, PRIMER_NOMBRE, " +
                "SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, " +
                "FECHA_CONSENTIMIENTO, HASHINTEGRIDADBIOMETRICA, ROLES_IDROL, PUESTOS_VOTACION_IDPUESTOS " +
                "FROM VOTANTES WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByIdentificacion - identificacion: " + identificacion, e);
        }
    }

    public List<Votante> findByIdRol(Long idRol) {
        String sql = "SELECT ID_VOTANTE, IDENTIFICACION, PLANTILLA_BIOMETRICA, CORREO, PRIMER_NOMBRE, " +
                "SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, " +
                "FECHA_CONSENTIMIENTO, HASHINTEGRIDADBIOMETRICA, ROLES_IDROL, PUESTOS_VOTACION_IDPUESTOS " +
                "FROM VOTANTES WHERE ROLES_IDROL = ? ORDER BY PRIMER_APELLIDO, PRIMER_NOMBRE";
        List<Votante> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idRol);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByIdRol - idRol: " + idRol, e);
        }
    }

    public List<Votante> findByIdPuesto(Long idPuesto) {
        String sql = "SELECT ID_VOTANTE, IDENTIFICACION, PLANTILLA_BIOMETRICA, CORREO, PRIMER_NOMBRE, " +
                "SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, " +
                "FECHA_CONSENTIMIENTO, HASHINTEGRIDADBIOMETRICA, ROLES_IDROL, PUESTOS_VOTACION_IDPUESTOS " +
                "FROM VOTANTES WHERE PUESTOS_VOTACION_IDPUESTOS = ? ORDER BY PRIMER_APELLIDO, PRIMER_NOMBRE";
        List<Votante> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idPuesto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByIdPuesto - idPuesto: " + idPuesto, e);
        }
    }

    public boolean estaHabilitado(String identificacion) {
        String sql = "SELECT ESTADO_VOTO FROM VOTANTES WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return EstadoVotante.PENDIENTE.name().equals(rs.getString("ESTADO_VOTO"));
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.estaHabilitado - identificacion: " + identificacion, e);
        }
    }

    public void actualizarEstado(String identificacion, String estado) {
        String sql = "UPDATE VOTANTES SET ESTADO_VOTO = ? WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado);
            ps.setString(2, identificacion);

            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el votante con identificación: " + identificacion);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.actualizarEstado - identificacion: " + identificacion, e);
        }
    }

    public void actualizarPlantilla(String identificacion, String templateCifrado, String hash) {
        String sql = "UPDATE VOTANTES SET PLANTILLA_BIOMETRICA = ?, HASHINTEGRIDADBIOMETRICA = ? " +
                "WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, templateCifrado);
            ps.setString(2, hash);
            ps.setString(3, identificacion);

            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el votante con identificación: " + identificacion);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.actualizarPlantilla - identificacion: " + identificacion, e);
        }
    }

    public void actualizarFoto(String identificacion, String fotoUrl) {
        String sql = "UPDATE VOTANTES SET FOTO_URL = ? WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fotoUrl);
            ps.setString(2, identificacion);

            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el votante con identificación: " + identificacion);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.actualizarFoto - identificacion: " + identificacion, e);
        }
    }

    public void anonimizarDatosBiometricos(String identificacion) {
        String sql = "UPDATE VOTANTES SET PLANTILLA_BIOMETRICA = NULL, HASHINTEGRIDADBIOMETRICA = NULL " +
                "WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);

            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el votante con identificación: " + identificacion);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.anonimizarDatosBiometricos - identificacion: " + identificacion, e);
        }
    }
}
