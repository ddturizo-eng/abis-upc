package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.EleccionRol;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class EleccionRolRepository {

    public void save(Long idEleccion, Long idRol, Double pesoVoto) {
        String sql = "INSERT INTO Eleccion_roles (ID_ELECCION, ID_ROL, PESO_VOTO, FECHA_CONFIGURACION) VALUES (?, ?, ?, SYSDATE)";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            ps.setLong(2, idRol);
            ps.setDouble(3, pesoVoto);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en EleccionRolRepository.save", e);
        }
    }

    public List<EleccionRol> findByEleccion(Long idEleccion) {
        String sql = "SELECT er.*, r.NOMBRE AS nombre_rol FROM Eleccion_roles er JOIN Roles r ON r.ID_ROL = er.ID_ROL WHERE er.ID_ELECCION = ?";
        List<EleccionRol> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EleccionRol er = new EleccionRol();
                    er.setIdEleccion(rs.getLong("ID_ELECCION"));
                    er.setIdRol(rs.getLong("ID_ROL"));
                    er.setPesoVoto(rs.getDouble("PESO_VOTO"));
                    er.setFechaConfiguracion(rs.getTimestamp("FECHA_CONFIGURACION"));
                    er.setNombreRol(rs.getString("nombre_rol"));
                    lista.add(er);
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en EleccionRolRepository.findByEleccion", e);
        }
    }

    public Double getPesoVoto(Long idEleccion, Long idRol) {
        String sql = "SELECT PESO_VOTO FROM Eleccion_roles WHERE ID_ELECCION = ? AND ID_ROL = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            ps.setLong(2, idRol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("PESO_VOTO");
                }
                return 1.0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en EleccionRolRepository.getPesoVoto", e);
        }
    }
}
