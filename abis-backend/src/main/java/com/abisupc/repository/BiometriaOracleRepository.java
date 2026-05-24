package com.abisupc.repository;

import com.abisupc.config.AppConfig;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;

public class BiometriaOracleRepository {

    public void enrolarBiometria(String identificacion, byte[] plantilla, String hash) throws SQLException {
        String sql = "{ call prc_enrolar_biometria(?, ?, ?) }";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setString(1, identificacion);
            cs.setBytes(2, plantilla);
            cs.setString(3, hash);
            cs.execute();
        }
    }
}
