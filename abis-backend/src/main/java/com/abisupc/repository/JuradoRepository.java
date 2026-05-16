package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Jurado;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JuradoRepository {

    private Jurado mapRow(ResultSet rs) throws SQLException {
        Jurado j = new Jurado();
        j.setIdMesa(rs.getLong("ID_MESA"));
        j.setIdentificacion(rs.getString("IDENTIFICACION"));
        java.sql.Date fechaAsignacion = rs.getDate("FECHA_ASIGNACION");
        j.setFechaAsignacion(fechaAsignacion != null ? fechaAsignacion.toLocalDate() : null);
        j.setCargo(rs.getString("CARGO"));
        return j;
    }

    public void save(Jurado jurado) {
        String sql = "INSERT INTO JURADOS (ID_MESA, IDENTIFICACION, FECHA_ASIGNACION, CARGO) " +
                "VALUES (?, ?, ?, ?)";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, jurado.getIdMesa());
            ps.setString(2, jurado.getIdentificacion());
            ps.setDate(3, Date.valueOf(jurado.getFechaAsignacion()));
            ps.setString(4, jurado.getCargo());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1)
                throw new RuntimeException("El votante " + jurado.getIdentificacion() +
                        " ya es jurado en la mesa " + jurado.getIdMesa(), e);
            if (e.getErrorCode() == 2291)
                throw new RuntimeException("No existe la mesa ID " + jurado.getIdMesa() +
                        " o el votante " + jurado.getIdentificacion(), e);
            throw new RuntimeException("Error en JuradoRepository.save", e);
        }
    }

    public List<Jurado> findByMesa(Long idMesa) {
        String sql = "SELECT ID_MESA, IDENTIFICACION, FECHA_ASIGNACION, CARGO " +
                "FROM JURADOS WHERE ID_MESA = ?";
        List<Jurado> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idMesa);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en JuradoRepository.findByMesa — idMesa: " + idMesa, e);
        }
    }

    public List<Jurado> findByIdentificacion(String identificacion) {
        String sql = "SELECT ID_MESA, IDENTIFICACION, FECHA_ASIGNACION, CARGO " +
                "FROM JURADOS WHERE IDENTIFICACION = ?";
        List<Jurado> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en JuradoRepository.findByIdentificacion — id: " + identificacion, e);
        }
    }

    public boolean esJurado(String identificacion) {
        String sql = "SELECT COUNT(*) FROM JURADOS WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en JuradoRepository.esJurado — id: " + identificacion, e);
        }
    }

    public void asignarAMesa(String identificacion, Long idMesa, String cargo) {
        String sql = "UPDATE JURADOS SET ID_MESA = ?, CARGO = ? WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idMesa);
            ps.setString(2, cargo);
            ps.setString(3, identificacion);
            int filas = ps.executeUpdate();
            if (filas == 0)
                throw new RuntimeException("No se encontró el jurado con identificacion: " + identificacion);
        } catch (SQLException e) {
            throw new RuntimeException("Error en JuradoRepository.asignarAMesa — id: " + identificacion, e);
        }
    }

    public void asignarJurado(Long idMesa, String identificacion, String cargo) throws SQLException {
        String sql = "{ call prc_asignar_jurado(?, ?, ?) }";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setLong(1, idMesa);
            cs.setString(2, identificacion);
            cs.setString(3, cargo);
            cs.execute();
        }
    }
}
