package com.abisupc.repository;

import com.abisupc.config.AppConfig;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

public class VotoOracleRepository {

    public void registrarVoto(String identificacion, Long idEleccion, Long idCandidato, Long idPuesto) throws SQLException {
        String sql = "{ call PKG_ELECTORAL.prc_registrar_voto(?, ?, ?, ?) }";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.setString(1, identificacion);
            cs.setLong(2, idEleccion);
            setNullableLong(cs, 3, idCandidato);
            cs.setLong(4, idPuesto);
            cs.execute();
        }
    }

    public double calcularVotosCandidato(Long idCandidato, Long idEleccion) throws SQLException {
        String sql = "{ ? = call fnc_calcular_votos_candidato(?, ?) }";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.registerOutParameter(1, Types.NUMERIC);
            cs.setLong(2, idCandidato);
            cs.setLong(3, idEleccion);
            cs.execute();
            return cs.getDouble(1);
        }
    }

    public double porcentajeParticipacion(Long idEleccion) throws SQLException {
        String sql = "{ ? = call fnc_porcentaje_participacion(?) }";
        try (Connection conn = AppConfig.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {
            cs.registerOutParameter(1, Types.NUMERIC);
            cs.setLong(2, idEleccion);
            cs.execute();
            return cs.getDouble(1);
        }
    }

    private void setNullableLong(CallableStatement cs, int index, Long value) throws SQLException {
        if (value == null) {
            cs.setNull(index, Types.NUMERIC);
        } else {
            cs.setLong(index, value);
        }
    }
}
