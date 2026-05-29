package com.abisupc.service;

import com.abisupc.config.AppConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sincroniza estados de elecciones con su ventana horaria.
 */
public class EleccionLifecycleService {

    public void sincronizarEstados() {
        try (Connection conn = AppConfig.getConnection()) {
            String inicioCol = fechaInicioColumn(conn);
            String finCol = fechaFinColumn(conn);
            cerrarVencidas(conn, finCol);
            iniciarVigenteSiNoHayActiva(conn, inicioCol, finCol);
        } catch (SQLException e) {
            throw new RuntimeException("No fue posible sincronizar estados electorales", e);
        }
    }

    public Optional<Map<String, Object>> eleccionEnCurso() {
        try (Connection conn = AppConfig.getConnection()) {
            String sql = "SELECT ID_ELECCION, NOMBRE FROM Elecciones WHERE ESTADO = 'EN_CURSO'";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", rs.getLong("ID_ELECCION"));
                data.put("nombre", rs.getString("NOMBRE"));
                return Optional.of(data);
            }
        } catch (SQLException e) {
            throw new RuntimeException("No fue posible consultar eleccion en curso", e);
        }
    }

    private void cerrarVencidas(Connection conn, String finCol) throws SQLException {
        String sql = "UPDATE Elecciones SET ESTADO = 'CERRADA' WHERE ESTADO = 'EN_CURSO' AND " + finCol + " <= SYSTIMESTAMP";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private void iniciarVigenteSiNoHayActiva(Connection conn, String inicioCol, String finCol) throws SQLException {
        if (hayActiva(conn)) {
            return;
        }
        String select = "SELECT ID_ELECCION FROM Elecciones WHERE ESTADO = 'PROGRAMADA' " +
                "AND " + inicioCol + " <= SYSTIMESTAMP AND " + finCol + " > SYSTIMESTAMP " +
                "ORDER BY " + inicioCol;
        try (PreparedStatement ps = conn.prepareStatement(select);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return;
            }
            try (PreparedStatement update = conn.prepareStatement("UPDATE Elecciones SET ESTADO = 'EN_CURSO' WHERE ID_ELECCION = ?")) {
                update.setLong(1, rs.getLong("ID_ELECCION"));
                update.executeUpdate();
            }
        }
    }

    private boolean hayActiva(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM Elecciones WHERE ESTADO = 'EN_CURSO'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private String fechaInicioColumn(Connection conn) throws SQLException {
        return columnExists(conn, "FECHAHORA_INICIO") ? "FECHAHORA_INICIO" : "FECHA_HORA_INICIO";
    }

    private String fechaFinColumn(Connection conn) throws SQLException {
        return columnExists(conn, "FECHAHORA_FIN") ? "FECHAHORA_FIN" : "FECHA_HORA_FIN";
    }

    private boolean columnExists(Connection conn, String columnName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'ELECCIONES' AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
