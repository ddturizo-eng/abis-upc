package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Eleccion;
import com.abisupc.model.EstadoEleccion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para la tabla {@code Elecciones} en Oracle.
 *
 * <p>Gestiona el ciclo de vida y la persistencia de los procesos y jornadas electorales
 * programados en la plataforma. Este componente provee la lógica necesaria para administrar
 * las transiciones de estado de las elecciones y realizar validaciones temporales críticas.
 *
 * <p>Al igual que otros repositorios del sistema, incorpora una estrategia de resolución
 * dinámica de columnas mediante metadatos relacionales ({@code USER_TAB_COLUMNS}) para soportar
 * variaciones físicas comunes en los campos cronológicos (ej. {@code FECHAHORA_INICIO}
 * vs {@code FECHA_HORA_INICIO}).
 *
 * <p>Tabla Oracle: {@code Elecciones}
 * <ul>
 * <li>{@code ID_ELECCION} — PK numérica administrada mediante la secuencia {@code seq_elecciones}.</li>
 * <li>{@code NOMBRE} — Denominación oficial dada al proceso electoral institucional.</li>
 * <li>{@code FECHAHORA_INICIO} / {@code FECHA_HORA_INICIO} — Estampa de tiempo del inicio del sufragio.</li>
 * <li>{@code FECHAHORA_FIN} / {@code FECHA_HORA_FIN} — Estampa de tiempo del cierre del sufragio.</li>
 * <li>{@code ESTADO} — Estado operacional indexado (Mapeado mediante {@link EstadoEleccion}).</li>
 * </ul>
 */
public class EleccionRepository implements Repository<Eleccion> {

    /**
     * Recupera la totalidad de los procesos electorales registrados en el sistema.
     *
     * <p>El conjunto de resultados se devuelve en un orden estrictamente cronológico inverso
     * basado en la fecha de apertura de las urnas.
     *
     * @return lista estructurada de todas las elecciones; nunca retorna {@code null}
     * @throws RuntimeException en caso de fallos técnicos de comunicación JDBC con Oracle
     */
    @Override
    public List<Eleccion> findAll() {
        List<Eleccion> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection()) {
            String inicioCol = fechaInicioColumn(conn);
            String finCol = fechaFinColumn(conn);
            String sql = "SELECT ID_ELECCION, NOMBRE, " + inicioCol + " AS FECHAHORA_INICIO, " +
                    finCol + " AS FECHAHORA_FIN, ESTADO FROM Elecciones ORDER BY " + inicioCol + " DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en EleccionRepository.findAll", e);
        }
    }

    /**
     * Busca una jornada electoral específica mediante su identificador primario.
     *
     * @param id identificador único del proceso electoral a localizar
     * @return {@link Optional} encapsulando la entidad si existe; un contenedor vacío en su defecto
     * @throws RuntimeException si ocurre una anomalía al mapear los tipos de datos relacionales
     */
    @Override
    public Optional<Eleccion> findById(Long id) {
        try (Connection conn = AppConfig.getConnection()) {
            String inicioCol = fechaInicioColumn(conn);
            String finCol = fechaFinColumn(conn);
            String sql = "SELECT ID_ELECCION, NOMBRE, " + inicioCol + " AS FECHAHORA_INICIO, " +
                    finCol + " AS FECHAHORA_FIN, ESTADO FROM Elecciones WHERE ID_ELECCION = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en EleccionRepository.findById - id: " + id, e);
        }
    }

    /**
     * Registra de manera persistente una nueva jornada electoral.
     *
     * <p>Toda nueva elección es inicializada por defecto y de manera determinista con el
     * estado operacional {@code 'PROGRAMADA'}. El identificador único es extraído tras la
     * inserción usando la secuencia {@code seq_elecciones.CURRVAL} sobre la tabla del sistema {@code DUAL}.
     *
     * @param e entidad de negocio elección con sus rangos de fecha y nombre parametrizados
     * @throws RuntimeException si se presentan problemas de conectividad o desborde de secuencia
     */
    @Override
    public void save(Eleccion e) {
        try (Connection conn = AppConfig.getConnection()) {
            String sql = "INSERT INTO Elecciones (ID_ELECCION, NOMBRE, " + fechaInicioColumn(conn) + ", " +
                    fechaFinColumn(conn) + ", ESTADO) " +
                    "VALUES (seq_elecciones.NEXTVAL, ?, ?, ?, 'PROGRAMADA')";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, e.getNombre());
                ps.setTimestamp(2, Timestamp.valueOf(e.getFechaHoraInicio()));
                ps.setTimestamp(3, Timestamp.valueOf(e.getFechaHoraFin()));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT seq_elecciones.CURRVAL FROM dual");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    e.setId(rs.getLong(1));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.save", ex);
        }
    }

    /**
     * Actualiza los metadatos de configuración básicos de una elección.
     *
     * <p><strong>Regla de Negocio Crítica:</strong> Modificaciones estructurales de nombres y
     * ventanas de tiempo solo se permiten si el registro se encuentra en estado {@code 'PROGRAMADA'}.
     * Una vez iniciada o finalizada la jornada, los parámetros del censo quedan congelados para auditoría.
     *
     * @param e entidad modificada portando el identificador a intervenir
     * @throws IllegalStateException si la elección ya no se encuentra en estado 'PROGRAMADA'
     * @throws RuntimeException si el motor Oracle interrumpe el procesamiento transaccional
     */
    @Override
    public void update(Eleccion e) {
        try (Connection conn = AppConfig.getConnection()) {
            String sql = "UPDATE Elecciones SET NOMBRE = ?, " + fechaInicioColumn(conn) + " = ?, " +
                    fechaFinColumn(conn) + " = ? WHERE ID_ELECCION = ? AND ESTADO = 'PROGRAMADA'";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, e.getNombre());
                ps.setTimestamp(2, Timestamp.valueOf(e.getFechaHoraInicio()));
                ps.setTimestamp(3, Timestamp.valueOf(e.getFechaHoraFin()));
                ps.setLong(4, e.getId());
                int filas = ps.executeUpdate();
                if (filas == 0) {
                    throw new IllegalStateException("Solo se puede editar una eleccion en estado PROGRAMADA");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.update - id: " + e.getId(), ex);
        }
    }

    /**
     * Transiciona el estado administrativo y operacional de una elección determinada.
     *
     * @param id identificador primario de la elección a modificar
     * @param nuevoEstado cadena representativa del nuevo estado lógico (ej. 'EN_CURSO', 'FINALIZADA')
     * @throws RuntimeException si se produce una violación de check-constraint en el motor relacional
     */
    public void cambiarEstado(Long id, String nuevoEstado) {
        String sql = "UPDATE Elecciones SET ESTADO = ? WHERE ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.cambiarEstado - id: " + id, ex);
        }
    }

    /**
     * Determina con agilidad si una elección ya cuenta con sufragios procesados en sus mesas.
     *
     * <p>Componente esencial de auditoría para salvaguardar la integridad de los resultados,
     * impidiendo limpiezas o cancelaciones arbitrarias sobre elecciones activas.
     *
     * @param id identificador de la elección objeto del censo
     * @return {@code true} si se registra al menos un sufragio amarrado a la FK; {@code false} en su defecto
     * @throws RuntimeException en caso de fallos de indexación en la base de datos
     */
    public boolean tieneVotos(Long id) {
        String sql = "SELECT COUNT(*) FROM Votos WHERE ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.tieneVotos - id: " + id, ex);
        }
    }

    /**
     * Valida de manera transversal si existe alguna jornada electoral abierta simultáneamente.
     *
     * <p>Garantiza la regla del negocio que impide la superposición o concurrencia de múltiples
     * elecciones simultáneas bajo el estado {@code 'EN_CURSO'}.
     *
     * @return {@code true} si el sistema detecta una contienda activa; {@code false} en caso contrario
     * @throws RuntimeException si el conteo relacional sobre el índice falla
     */
    public boolean hayEleccionEnCurso() {
        String sql = "SELECT COUNT(*) FROM Elecciones WHERE ESTADO = 'EN_CURSO'";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.hayEleccionEnCurso", ex);
        }
    }

    /**
     * Remueve físicamente una elección del maestro por medio de su clave primaria.
     *
     * @param id identificador único del registro a eliminar
     * @throws RuntimeException si se viola alguna restricción FK de integridad referencial
     * por registros huérfanos asociados en cascada
     */
    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM Elecciones WHERE ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.delete - id: " + id, ex);
        }
    }

    /**
     * Localiza de manera directa la jornada electoral activa concurrente del ecosistema.
     *
     * @return {@link Optional} que contiene la elección {@code 'EN_CURSO'} si existiese,
     * o un contenedor vacío en caso de encontrarse las votaciones cerradas
     */
    public Optional<Eleccion> findActiva() {
        return findByEstado("EN_CURSO").stream().findFirst();
    }

    /**
     * Filtra el conjunto de elecciones compartiendo una misma bandera de estado operacional.
     *
     * @param estado literal representativo del estado que se desea listar
     * @return lista de elecciones que satisfacen el criterio relacional, ordenadas por fecha de inicio decreciente
     * @throws RuntimeException si se produce una falla crítica en el canal JDBC
     */
    public List<Eleccion> findByEstado(String estado) {
        List<Eleccion> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection()) {
            String inicioCol = fechaInicioColumn(conn);
            String finCol = fechaFinColumn(conn);
            String sql = "SELECT ID_ELECCION, NOMBRE, " + inicioCol + " AS FECHAHORA_INICIO, " +
                    finCol + " AS FECHAHORA_FIN, ESTADO FROM Elecciones WHERE ESTADO = ? ORDER BY " + inicioCol + " DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, estado);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(mapRow(rs));
                    }
                    return lista;
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error en EleccionRepository.findByEstado - estado: " + estado, ex);
        }
    }

    /**
     * Método delegado utilitario de compatibilidad para disparar la actualización de estados.
     *
     * @param idEleccion clave primaria del proceso electoral
     * @param estado descriptor textual del nuevo estado
     * @see #cambiarEstado(Long, String)
     */
    public void actualizarEstado(Long idEleccion, String estado) {
        cambiarEstado(idEleccion, estado);
    }

    /**
     * Transforma una fila posicional del cursor {@link ResultSet} en un objeto de dominio {@link Eleccion}.
     *
     * <p>Efectúa la conversión tipada y segura de los objetos de tiempo {@link Timestamp} del driver
     * de base de datos hacia las especificaciones tipadas de la API {@link java.time.LocalDateTime}.
     */
    private Eleccion mapRow(ResultSet rs) throws SQLException {
        Eleccion eleccion = new Eleccion();
        Timestamp inicio = rs.getTimestamp("FECHAHORA_INICIO");
        Timestamp fin = rs.getTimestamp("FECHAHORA_FIN");

        eleccion.setId(rs.getLong("ID_ELECCION"));
        eleccion.setNombre(rs.getString("NOMBRE"));
        eleccion.setFechaHoraInicio(inicio != null ? inicio.toLocalDateTime() : null);
        eleccion.setFechaHoraFin(fin != null ? fin.toLocalDateTime() : null);
        eleccion.setEstado(EstadoEleccion.fromDb(rs.getString("ESTADO")));

        return eleccion;
    }

    /**
     * Resuelve el nombre físico real del atributo de fecha de inicio en la tabla.
     */
    private String fechaInicioColumn(Connection conn) throws SQLException {
        return columnExists(conn, "ELECCIONES", "FECHAHORA_INICIO") ? "FECHAHORA_INICIO" : "FECHA_HORA_INICIO";
    }

    /**
     * Resuelve el nombre físico real del atributo de fecha de finalización en la tabla.
     */
    private String fechaFinColumn(Connection conn) throws SQLException {
        return columnExists(conn, "ELECCIONES", "FECHAHORA_FIN") ? "FECHAHORA_FIN" : "FECHA_HORA_FIN";
    }

    /**
     * Consulta de forma preventiva el catálogo del esquema en Oracle para validar la existencia de una columna.
     *
     * @param conn conexión JDBC transaccional activa
     * @param tableName nombre exacto del objeto tabla en mayúsculas
     * @param columnName nombre exacto de la columna bajo escrutinio
     * @return {@code true} si el atributo existe físicamente en {@code USER_TAB_COLUMNS}; {@code false} de lo contrario
     * @throws SQLException si falla el escaneo de los diccionarios internos del motor relacional
     */
    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
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