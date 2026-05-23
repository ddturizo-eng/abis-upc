package com.abisupc.repository;

import com.abisupc.config.AppConfig;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;

public class VotanteAdminRepository {

    public void inhabilitarVotante(String identificacion, Long idAdmin, String motivo) throws SQLException {
        String sql = "{ call PKG_VOTANTES.prc_inhabilitar(?, ?, ?) }";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setString(1, identificacion);
            cs.setLong(2, idAdmin);
            cs.setString(3, motivo);
            cs.execute();
        }
    }

    public void habilitarVotante(String identificacion, Long idAdmin, String motivo) throws SQLException {
        String sql = "{ call PKG_VOTANTES.prc_habilitar(?, ?, ?) }";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setString(1, identificacion);
            cs.setLong(2, idAdmin);
            cs.setString(3, motivo);
            cs.execute();
        }
    }

    public Map<String, String> votantePuedeVotar(String identificacion, Long idEleccion) throws SQLException {
        String sql = "{ ? = call PKG_VOTANTES.fn_puede_votar(?, ?, ?) }";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.registerOutParameter(1, Types.VARCHAR);
            cs.setString(2, identificacion);
            cs.setLong(3, idEleccion);
            cs.registerOutParameter(4, Types.VARCHAR);
            cs.execute();

            Map<String, String> result = new LinkedHashMap<>();
            result.put("puede", cs.getString(1));
            result.put("motivo", cs.getString(4));
            return result;
        }
    }
}
