package com.abisupc.service;

import com.abisupc.config.AppConfig;
import com.abisupc.repository.VotoOracleRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResultadosService {

    private final VotoOracleRepository votoRepo;

    public ResultadosService() {
        this(new VotoOracleRepository());
    }

    public ResultadosService(VotoOracleRepository votoRepo) {
        this.votoRepo = votoRepo;
    }

    public Map<String, Object> resultadosEleccion(Long idEleccion) throws SQLException {
        if (idEleccion == null || idEleccion <= 0) {
            throw new IllegalArgumentException("idEleccion requerido");
        }

        List<Map<String, Object>> candidatos = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection()) {
            String pesoCol = pesoColumn(conn);
            String sql = "SELECT c.PRIMER_NOMBRE || ' ' || c.PRIMER_APELLIDO AS NOMBRE, " +
                    "c.ID_CANDIDATO, c.SEGUNDO_NOMBRE, c.SEGUNDO_APELLIDO, " +
                    "ce.NUMERO_CAMPANIA, ce.CARGO, ce.ID_ELECCION, " +
                    "NVL(SUM(v." + pesoCol + "), 0) AS TOTAL_PESO " +
                    "FROM Votos v " +
                    "LEFT JOIN Candidatos_eleccion ce " +
                    "ON v.ID_CANDIDATO = ce.ID_CANDIDATO AND v.ID_ELECCION = ce.ID_ELECCION " +
                    "LEFT JOIN Candidatos c ON c.ID_CANDIDATO = ce.ID_CANDIDATO " +
                    "WHERE v.ID_ELECCION = ? " +
                    "GROUP BY c.PRIMER_NOMBRE, c.PRIMER_APELLIDO, c.ID_CANDIDATO, " +
                    "c.SEGUNDO_NOMBRE, c.SEGUNDO_APELLIDO, ce.NUMERO_CAMPANIA, ce.CARGO, ce.ID_ELECCION " +
                    "ORDER BY TOTAL_PESO DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, idEleccion);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        Long idCandidato = rs.getLong("ID_CANDIDATO");
                        if (rs.wasNull()) {
                            item.put("idCandidato", null);
                            item.put("nombre", "Voto en blanco");
                            item.put("cargo", "VOTO EN BLANCO");
                            item.put("numeroCampania", 0);
                        } else {
                            item.put("idCandidato", idCandidato);
                            item.put("nombre", rs.getString("NOMBRE"));
                            item.put("cargo", rs.getString("CARGO"));
                            item.put("numeroCampania", rs.getInt("NUMERO_CAMPANIA"));
                        }
                        item.put("idEleccion", idEleccion);
                        item.put("votosPonderados", rs.getDouble("TOTAL_PESO"));
                        candidatos.add(item);
                    }
                }
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("idEleccion", idEleccion);
        response.put("porcentajeParticipacion", votoRepo.porcentajeParticipacion(idEleccion));
        response.put("candidatos", candidatos);
        return response;
    }

    private static String pesoColumn(Connection conn) throws SQLException {
        return columnExists(conn, "VOTOS", "PESO_VOTO_APLICADO") ? "PESO_VOTO_APLICADO" : "PESOVOTO_APLICADO";
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
