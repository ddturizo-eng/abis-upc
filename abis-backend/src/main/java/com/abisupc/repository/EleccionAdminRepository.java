package com.abisupc.repository;

import com.abisupc.config.AppConfig;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;

public class EleccionAdminRepository {

    public void cerrarEleccion(Long idEleccion, Long idAdmin) throws SQLException {
        String sql = "{ call PKG_ELECTORAL.prc_cerrar_eleccion(?, ?) }";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setLong(1, idEleccion);
            cs.setLong(2, idAdmin);
            cs.execute();
        }
    }
}
