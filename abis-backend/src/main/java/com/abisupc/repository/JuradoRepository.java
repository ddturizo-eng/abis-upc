package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Jurado;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JuradoRepository {

    private Jurado mapRow(ResultSet rs) throws SQLException {
        Jurado j = new Jurado();
        j.setIdMesa(rs.getLong("MESA_JURADOS_IDMESA"));
        j.setIdentificacion(rs.getString("VOTANTES_IDENTIFICACION"));
        // DATE de Oracle → java.sql.Date → LocalDate de Java
        j.setFechaAsignacion(rs.getDate("FECHA_ASIGNACION").toLocalDate());
        j.setCargo(rs.getString("CARGO"));
        return j;
    }

    public void save(Jurado jurado) {
        String sql = "INSERT INTO JURADOS (MESA_JURADOS_IDMESA, VOTANTES_IDENTIFICACION, FECHA_ASIGNACION, CARGO) " +
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
        String sql = "SELECT MESA_JURADOS_IDMESA, VOTANTES_IDENTIFICACION, FECHA_ASIGNACION, CARGO " +
                "FROM JURADOS WHERE MESA_JURADOS_IDMESA = ?";
        List<Jurado> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idMesa);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en JuradoRepository.findByMesa — idMesa: " + idMesa, e);
        }
    }

    public List<Jurado> findByIdentificacion(String identificacion) {
        String sql = "SELECT MESA_JURADOS_IDMESA, VOTANTES_IDENTIFICACION, FECHA_ASIGNACION, CARGO " +
                "FROM JURADOS WHERE VOTANTES_IDENTIFICACION = ?";
        List<Jurado> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en JuradoRepository.findByIdentificacion — id: " + identificacion, e);
        }
    }

    public boolean esJurado(String identificacion) {
        String sql = "SELECT COUNT(*) FROM JURADOS WHERE VOTANTES_IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error en JuradoRepository.esJurado — id: " + identificacion, e);
        }
    }

    public void asignarAMesa(String identificacion, Long idMesa, String cargo) {
        String sql = "UPDATE JURADOS SET MESA_JURADOS_IDMESA = ?, CARGO = ? WHERE VOTANTES_IDENTIFICACION = ?";
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
}