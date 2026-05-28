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
 * Sincroniza los estados de las elecciones con su ventana horaria definida.
 *
 * <p>Se invoca al arrancar el servidor en {@code AppServer.main()} para corregir
 * cualquier inconsistencia que haya quedado de sesiones anteriores (ej: el servidor
 * se apago mientras una eleccion estaba en curso y no se cerro correctamente).
 *
 * <p>Logica de sincronizacion:
 * <ol>
 *   <li>Cierra todas las elecciones {@code EN_CURSO} cuya {@code FECHA_HORA_FIN}
 *       ya haya pasado.</li>
 *   <li>Si no hay ninguna eleccion activa, inicia la primera eleccion
 *       {@code PROGRAMADA} cuya ventana horaria este vigente.</li>
 * </ol>
 *
 * <p>Detecta en tiempo de ejecucion si la columna de fecha se llama
 * {@code FECHAHORA_INICIO} o {@code FECHA_HORA_INICIO} para ser compatible
 * con distintas versiones del esquema Oracle.
 */
public class EleccionLifecycleService {

    /**
     * Ejecuta la sincronizacion completa de estados electorales.
     *
     * @throws RuntimeException si no es posible conectar a Oracle o ejecutar las consultas
     */
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

    /**
     * Retorna la eleccion actualmente en curso, si existe.
     *
     * @return {@link Optional} con un mapa de {@code id} y {@code nombre}
     *         de la eleccion en curso, vacio si no hay ninguna
     * @throws RuntimeException si falla el acceso a la base de datos
     */
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

    /**
     * Cierra todas las elecciones en curso cuya fecha de fin ya haya pasado.
     *
     * @param conn   conexion activa
     * @param finCol nombre real de la columna de fecha fin en Oracle
     * @throws SQLException si falla el UPDATE
     */
    private void cerrarVencidas(Connection conn, String finCol) throws SQLException {
        String sql = "UPDATE Elecciones SET ESTADO = 'CERRADA' WHERE ESTADO = 'EN_CURSO' AND "
                + finCol + " <= SYSTIMESTAMP";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    /**
     * Inicia la primera eleccion programada vigente si no hay ninguna activa.
     *
     * <p>Solo inicia una eleccion si su ventana horaria ({@code INICIO <= AHORA < FIN})
     * esta activa y no existe ya una eleccion {@code EN_CURSO}.
     *
     * @param conn      conexion activa
     * @param inicioCol nombre real de la columna de fecha inicio
     * @param finCol    nombre real de la columna de fecha fin
     * @throws SQLException si fallan las consultas
     */
    private void iniciarVigenteSiNoHayActiva(Connection conn, String inicioCol, String finCol) throws SQLException {
        if (hayActiva(conn)) {
            return;
        }
        String select = "SELECT ID_ELECCION FROM Elecciones WHERE ESTADO = 'PROGRAMADA' "
                + "AND " + inicioCol + " <= SYSTIMESTAMP AND " + finCol + " > SYSTIMESTAMP "
                + "ORDER BY " + inicioCol;
        try (PreparedStatement ps = conn.prepareStatement(select);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return;
            }
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE Elecciones SET ESTADO = 'EN_CURSO' WHERE ID_ELECCION = ?")) {
                update.setLong(1, rs.getLong("ID_ELECCION"));
                update.executeUpdate();
            }
        }
    }

    /**
     * Verifica si existe al menos una eleccion en curso.
     *
     * @param conn conexion activa
     * @return {@code true} si hay una eleccion {@code EN_CURSO}
     * @throws SQLException si falla la consulta
     */
    private boolean hayActiva(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM Elecciones WHERE ESTADO = 'EN_CURSO'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * Detecta el nombre real de la columna de fecha inicio en {@code ELECCIONES}.
     *
     * @param conn conexion activa
     * @return {@code "FECHAHORA_INICIO"} si existe, o {@code "FECHA_HORA_INICIO"}
     * @throws SQLException si falla la consulta de metadatos
     */
    private String fechaInicioColumn(Connection conn) throws SQLException {
        return columnExists(conn, "FECHAHORA_INICIO") ? "FECHAHORA_INICIO" : "FECHA_HORA_INICIO";
    }

    /**
     * Detecta el nombre real de la columna de fecha fin en {@code ELECCIONES}.
     *
     * @param conn conexion activa
     * @return {@code "FECHAHORA_FIN"} si existe, o {@code "FECHA_HORA_FIN"}
     * @throws SQLException si falla la consulta de metadatos
     */
    private String fechaFinColumn(Connection conn) throws SQLException {
        return columnExists(conn, "FECHAHORA_FIN") ? "FECHAHORA_FIN" : "FECHA_HORA_FIN";
    }

    /**
     * Verifica si una columna existe en la tabla {@code ELECCIONES}.
     *
     * @param conn       conexion activa
     * @param columnName nombre de la columna en mayusculas
     * @return {@code true} si la columna existe
     * @throws SQLException si falla la consulta de metadatos
     */
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