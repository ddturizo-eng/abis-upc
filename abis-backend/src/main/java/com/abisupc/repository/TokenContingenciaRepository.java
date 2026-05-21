package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.TokenContingencia;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

public class TokenContingenciaRepository {

    public TokenContingencia guardarOReemplazar(String identificacion, Long idEleccion, String tokenHash, String tokenHint) {
        String sql = """
                MERGE INTO tokens_contingencia t
                USING (SELECT ? identificacion, ? id_eleccion FROM dual) src
                ON (t.identificacion = src.identificacion AND t.id_eleccion = src.id_eleccion)
                WHEN MATCHED THEN UPDATE SET
                    t.token_hash = ?,
                    t.token_hint = ?,
                    t.estado = 'ACTIVO',
                    t.fecha_emision = SYSTIMESTAMP,
                    t.fecha_expiracion = NULL,
                    t.fecha_uso = NULL,
                    t.id_puesto_uso = NULL,
                    t.scanner_id = NULL
                WHEN NOT MATCHED THEN INSERT (
                    id_token, identificacion, id_eleccion, token_hash, token_hint, estado, fecha_emision
                ) VALUES (
                    seq_tokens_contingencia.NEXTVAL, ?, ?, ?, ?, 'ACTIVO', SYSTIMESTAMP
                )
                """;
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ps.setLong(2, idEleccion);
            ps.setString(3, tokenHash);
            ps.setString(4, tokenHint);
            ps.setString(5, identificacion);
            ps.setLong(6, idEleccion);
            ps.setString(7, tokenHash);
            ps.setString(8, tokenHint);
            ps.executeUpdate();
            return findByIdentificacionEleccion(identificacion, idEleccion)
                    .orElseThrow(() -> new IllegalStateException("No se pudo leer el token generado"));
        } catch (SQLException e) {
            throw new RuntimeException("Error guardando token de contingencia", e);
        }
    }

    public Optional<TokenContingencia> findByHash(String tokenHash) {
        String sql = selectBase() + " WHERE TOKEN_HASH = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error consultando token de contingencia", e);
        }
    }

    public Optional<TokenContingencia> findByIdentificacionEleccion(String identificacion, Long idEleccion) {
        String sql = selectBase() + " WHERE IDENTIFICACION = ? AND ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ps.setLong(2, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error consultando token por votante y eleccion", e);
        }
    }

    private String selectBase() {
        return "SELECT ID_TOKEN, IDENTIFICACION, ID_ELECCION, TOKEN_HASH, TOKEN_HINT, ESTADO, " +
                "FECHA_EMISION, FECHA_EXPIRACION, FECHA_USO, ID_PUESTO_USO, SCANNER_ID " +
                "FROM tokens_contingencia";
    }

    private TokenContingencia mapRow(ResultSet rs) throws SQLException {
        TokenContingencia token = new TokenContingencia();
        token.setIdToken(rs.getLong("ID_TOKEN"));
        token.setIdentificacion(rs.getString("IDENTIFICACION"));
        token.setIdEleccion(rs.getLong("ID_ELECCION"));
        token.setTokenHash(rs.getString("TOKEN_HASH"));
        token.setTokenHint(rs.getString("TOKEN_HINT"));
        token.setEstado(rs.getString("ESTADO"));
        token.setFechaEmision(localDateTime(rs.getTimestamp("FECHA_EMISION")));
        token.setFechaExpiracion(localDateTime(rs.getTimestamp("FECHA_EXPIRACION")));
        token.setFechaUso(localDateTime(rs.getTimestamp("FECHA_USO")));
        long idPuestoUso = rs.getLong("ID_PUESTO_USO");
        token.setIdPuestoUso(rs.wasNull() ? null : idPuestoUso);
        token.setScannerId(rs.getString("SCANNER_ID"));
        return token;
    }

    private java.time.LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}
