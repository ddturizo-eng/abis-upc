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
        votante.setHashIntegridadBiometrica(rs.getString("HASH_INTEGRIDAD_BIOMETRICA"));
        votante.setIdRol(rs.getLong("ROLES_IDROL"));
        votante.setIdPuesto(rs.getLong("PUESTOS_VOTACION_IDPUESTOS"));

        return votante;
    }

    @Override
    public Optional<Votante> findById(Long id) {
        return findByIdentificacion(String.valueOf(id));
    }

    @Override
    public List<Votante> findAll() {
        String sql = "SELECT IDENTIFICACION, PLANTILLA_BIOMETRICA, CORREO, PRIMER_NOMBRE, " +
                "SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, " +
                "FECHA_CONSENTIMIENTO, HASH_INTEGRIDAD_BIOMETRICA, ROLES_IDROL, PUESTOS_VOTACION_IDPUESTOS " +
                "FROM VOTANTES ORDER BY PRIMER_APELLIDO, PRIMER_NOMBRE";
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
        String sql = "INSERT INTO VOTANTES (IDENTIFICACION, PLANTILLA_BIOMETRICA, CORREO, PRIMER_NOMBRE, " +
                "SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, FECHA_CONSENTIMIENTO, " +
                "HASH_INTEGRIDAD_BIOMETRICA, ROLES_IDROL, PUESTOS_VOTACION_IDPUESTOS) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getIdentificacion());
            ps.setString(2, entity.getPlantillaBiometrica());
            ps.setString(3, entity.getCorreo());
            ps.setString(4, entity.getPrimerNombre());
            ps.setString(5, entity.getSegundoNombre());
            ps.setString(6, entity.getPrimerApellido());
            ps.setString(7, entity.getSegundoApellido());
            ps.setString(8, entity.getEstadoVoto() != null ? entity.getEstadoVoto() : "PENDIENTE");
            ps.setString(9, entity.getFotoUrl());
            ps.setTimestamp(10, entity.getFechaConsentimiento());
            ps.setString(11, entity.getHashIntegridadBiometrica());
            ps.setLong(12, entity.getIdRol());
            ps.setLong(13, entity.getIdPuesto());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1) {
                throw new RuntimeException("Ya existe un votante con la identificación: " + entity.getIdentificacion(), e);
            }
            if (e.getErrorCode() == 22919) {
                throw new RuntimeException("El rol o puesto de votación no existe.", e);
            }
            throw new RuntimeException("Error en VotanteRepository.save", e);
        }
    }

    @Override
    public void update(Votante entity) {
        String sql = "UPDATE VOTANTES SET PLANTILLA_BIOMETRICA = ?, CORREO = ?, PRIMER_NOMBRE = ?, " +
                "SEGUNDO_NOMBRE = ?, PRIMER_APELLIDO = ?, SEGUNDO_APELLIDO = ?, ESTADO_VOTO = ?, FOTO_URL = ?, " +
                "FECHA_CONSENTIMIENTO = ?, HASH_INTEGRIDAD_BIOMETRICA = ?, ROLES_IDROL = ?, PUESTOS_VOTACION_IDPUESTOS = ? " +
                "WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getPlantillaBiometrica());
            ps.setString(2, entity.getCorreo());
            ps.setString(3, entity.getPrimerNombre());
            ps.setString(4, entity.getSegundoNombre());
            ps.setString(5, entity.getPrimerApellido());
            ps.setString(6, entity.getSegundoApellido());
            ps.setString(7, entity.getEstadoVoto());
            ps.setString(8, entity.getFotoUrl());
            ps.setTimestamp(9, entity.getFechaConsentimiento());
            ps.setString(10, entity.getHashIntegridadBiometrica());
            ps.setLong(11, entity.getIdRol());
            ps.setLong(12, entity.getIdPuesto());
            ps.setString(13, entity.getIdentificacion());

            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el votante con identificación: " + entity.getIdentificacion());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.update - identificacion: " + entity.getIdentificacion(), e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM VOTANTES WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(id));
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontró el votante con identificación: " + id);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 2292) {
                throw new RuntimeException("No se puede eliminar el votante " + id + " porque tiene registros asociados.", e);
            }
            throw new RuntimeException("Error en VotanteRepository.delete - identificacion: " + id, e);
        }
    }

    public Optional<Votante> findByIdentificacion(String identificacion) {
        String sql = "SELECT IDENTIFICACION, PLANTILLA_BIOMETRICA, CORREO, PRIMER_NOMBRE, " +
                "SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, " +
                "FECHA_CONSENTIMIENTO, HASH_INTEGRIDAD_BIOMETRICA, ROLES_IDROL, PUESTOS_VOTACION_IDPUESTOS " +
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

    public Optional<Votante> findByCorreo(String correo) {
        String sql = "SELECT IDENTIFICACION, PLANTILLA_BIOMETRICA, CORREO, PRIMER_NOMBRE, " +
                "SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, " +
                "FECHA_CONSENTIMIENTO, HASH_INTEGRIDAD_BIOMETRICA, ROLES_IDROL, PUESTOS_VOTACION_IDPUESTOS " +
                "FROM VOTANTES WHERE CORREO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, correo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByCorreo - correo: " + correo, e);
        }
    }

    public List<Votante> findByIdRol(Long idRol) {
        String sql = "SELECT IDENTIFICACION, PLANTILLA_BIOMETRICA, CORREO, PRIMER_NOMBRE, " +
                "SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, " +
                "FECHA_CONSENTIMIENTO, HASH_INTEGRIDAD_BIOMETRICA, ROLES_IDROL, PUESTOS_VOTACION_IDPUESTOS " +
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
        String sql = "SELECT IDENTIFICACION, PLANTILLA_BIOMETRICA, CORREO, PRIMER_NOMBRE, " +
                "SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, " +
                "FECHA_CONSENTIMIENTO, HASH_INTEGRIDAD_BIOMETRICA, ROLES_IDROL, PUESTOS_VOTACION_IDPUESTOS " +
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
        String sql = "UPDATE VOTANTES SET PLANTILLA_BIOMETRICA = ?, HASH_INTEGRIDAD_BIOMETRICA = ? " +
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
        String sql = "UPDATE VOTANTES SET PLANTILLABIOMETRICA = NULL, HASHINTEGRIDADBIOMETRICA = NULL " +
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
