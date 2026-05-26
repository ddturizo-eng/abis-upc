package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Voto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repositorio para la tabla {@code VOTOS} en Oracle.
 *
 * <p>Gestiona el acceso y procesamiento de los sufragios registrados en el sistema.
 * Por motivos de seguridad, auditoría e inmutabilidad electoral, este repositorio es de
 * <strong>solo lectura</strong> desde la capa de Java; las mutaciones físicas están restringidas.
 *
 * <p>La clase implementa una estrategia de compatibilidad dinámica mediante consultas al
 * diccionario de datos de Oracle ({@code USER_TAB_COLUMNS}) para resolver variaciones en el
 * esquema físico de la tabla, adaptándose automáticamente si las columnas clave usan
 * nomenclaturas alternativas (ej. {@code ID_VOTO} vs {@code ID_VOTOS}).
 *
 * <p>Tabla Oracle: {@code VOTOS}
 * <ul>
 * <li>{@code ID_VOTO} / {@code ID_VOTOS} — Clave primaria del sufragio.</li>
 * <li>{@code ID_ELECCION} — FK que vincula el voto con una jornada electoral específica.</li>
 * <li>{@code ID_CANDIDATO} — FK al candidato elegido. Un valor {@code NULL} representa un voto en blanco.</li>
 * <li>{@code FECHA_HORA} — Marca de tiempo precisa del registro del sufragio.</li>
 * <li>{@code PESO_VOTO_APLICADO} / {@code PESOVOTO_APLICADO} — Factor multiplicador del voto según coeficientes estatuarios.</li>
 * </ul>
 */
public class VotoRepository implements Repository<Voto> {

    /**
     * Convierte una fila del {@link ResultSet} en un objeto de dominio {@link Voto}.
     *
     * <p>El mapeo utiliza alias de columna estructurados ({@code ID_VOTO_ALIAS} y
     * {@code PESO_VOTO_ALIAS}) calculados dinámicamente en tiempo de ejecución, aislando
     * al objeto de negocio de las diferencias del esquema en la base de datos.
     *
     * @param rs cursor posicionado en la fila activa a mapear
     * @return objeto {@link Voto} con los datos de la fila relacional
     * @throws SQLException si ocurre un error al extraer los campos del ResultSet
     */
    private Voto mapRow(ResultSet rs) throws SQLException {
        Voto voto = new Voto();

        voto.setId(rs.getLong("ID_VOTO_ALIAS"));
        voto.setIdEleccion(rs.getLong("ID_ELECCION"));
        Long idCandidato = rs.getLong("ID_CANDIDATO");
        voto.setIdCandidato(rs.wasNull() ? null : idCandidato);
        voto.setFechaHora(rs.getTimestamp("FECHA_HORA"));
        voto.setPesoVotoAplicado(rs.getDouble("PESO_VOTO_ALIAS"));

        return voto;
    }

    /**
     * Busca un voto por su identificador primario.
     *
     * <p>Resuelve el nombre de la columna identificadora de manera dinámica antes
     * de preparar y ejecutar la sentencia parametrizada.
     *
     * @param id identificador único del sufragio
     * @return {@link Optional} con el voto si existe, o un contenedor vacío si no se encuentra
     * @throws RuntimeException si se produce un error de acceso a datos en la sesión Oracle
     */
    @Override
    public Optional<Voto> findById(Long id) {
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectBase(conn) + " WHERE " + idColumn(conn) + " = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.findById - id: " + id, e);
        }
    }

    /**
     * Recupera la totalidad de los votos almacenados en la tabla.
     *
     * <p>Ordena el conjunto resultante bajo el identificador determinado dinámicamente.
     *
     * @return lista con todos los registros de sufragio; nunca retorna {@code null}
     * @throws RuntimeException si se presenta un fallo crítico en la consulta JDBC
     */
    @Override
    public List<Voto> findAll() {
        List<Voto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectBase(conn) + " ORDER BY " + idColumn(conn));
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapRow(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.findAll", e);
        }
    }

    /**
     * Operación denegada de manera explícita.
     *
     * <p>Los votos no pueden ser insertados mediante sentencias directas de manipulación de datos (DML).
     * Su registro es competencia única del procedimiento almacenado transaccional {@code prc_registrar_voto}.
     *
     * @param entity entidad voto a registrar
     * @throws UnsupportedOperationException invariablemente al invocar este método
     */
    @Override
    public void save(Voto entity) {
        throw new UnsupportedOperationException("Los votos solo se registran mediante prc_registrar_voto");
    }

    /**
     * Operación denegada de manera explícita.
     *
     * <p>Garantiza el principio de inmutabilidad del censo y los resultados electorales.
     * No se permiten actualizaciones sobre votos ya emitidos.
     *
     * @param entity entidad con las modificaciones
     * @throws UnsupportedOperationException invariablemente al invocar este método
     */
    @Override
    public void update(Voto entity) {
        throw new UnsupportedOperationException("Los votos son inmutables y no se actualizan desde Java");
    }

    /**
     * Operación denegada de manera explícita.
     *
     * <p>Por normatividad legal y auditoría del sistema, un sufragio asentado bajo ninguna
     * circunstancia puede ser eliminado de la base de datos.
     *
     * @param id identificador del sufragio a remover
     * @throws UnsupportedOperationException invariablemente al invocar este método
     */
    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("Los votos son inmutables y no se eliminan desde Java");
    }

    /**
     * Recupera cronológicamente los votos asociados a un proceso electoral específico.
     *
     * @param idEleccion identificador único de la jornada electoral
     * @return lista de votos emitidos en dicha elección, ordenados desde el más reciente
     * @throws RuntimeException si falla la ejecución de la consulta relacional
     */
    public List<Voto> findByEleccion(Long idEleccion) {
        List<Voto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectBase(conn) + " WHERE ID_ELECCION = ? ORDER BY FECHA_HORA DESC")) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.findByEleccion - idEleccion: " + idEleccion, e);
        }
    }

    /**
     * Cuenta la cantidad total de votos acumulados por un candidato determinado.
     *
     * <p>Contempla una bifurcación lógica en la composición del SQL: si el parámetro
     * es {@code null}, evalúa mediante la condición {@code IS NULL} para totalizar
     * los votos en blanco.
     *
     * @param idCandidato identificador del candidato, o {@code null} para escrutar votos en blanco
     * @return sumatoria total de votos computados bajo esa condición
     * @throws RuntimeException si el motor de base de datos interrumpe la agregación
     */
    public int countByCandidato(Long idCandidato) {
        String sql = idCandidato == null
                ? "SELECT COUNT(*) FROM Votos WHERE ID_CANDIDATO IS NULL"
                : "SELECT COUNT(*) FROM Votos WHERE ID_CANDIDATO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (idCandidato != null) {
                ps.setLong(1, idCandidato);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.countByCandidato - idCandidato: " + idCandidato, e);
        }
    }

    /**
     * Realiza el escrutinio estándar directo de una elección mediante agregación.
     *
     * <p>Agrupa los totales utilizando la cláusula {@code GROUP BY ID_CANDIDATO}.
     * Los votos en blanco quedan representados en el mapa resultante asociados a la clave {@code null}.
     *
     * @param idEleccion identificador de la elección a totalizar
     * @return {@link Map} cuyas claves son los IDs de candidatos y valores la cantidad entera de votos
     * @throws RuntimeException si se produce una anomalía en la agrupación de los registros
     */
    public Map<Long, Integer> obtenerResultados(Long idEleccion) {
        String sql = "SELECT ID_CANDIDATO, COUNT(*) AS TOTAL_VOTOS FROM Votos " +
                "WHERE ID_ELECCION = ? GROUP BY ID_CANDIDATO";
        Map<Long, Integer> resultados = new HashMap<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Long idCandidato = rs.getLong("ID_CANDIDATO");
                    resultados.put(rs.wasNull() ? null : idCandidato, rs.getInt("TOTAL_VOTOS"));
                }
                return resultados;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.obtenerResultados - idEleccion: " + idEleccion, e);
        }
    }

    /**
     * Realiza el escrutinio ponderado aplicando el peso específico de cada voto emitido.
     *
     * <p>Utiliza la función de agregación {@code SUM} combinada con la columna de ponderación
     * resuelta dinámicamente, útil para escenarios de votación estatuaria o corporativa.
     *
     * @param idEleccion identificador de la elección objeto del cálculo ponderado
     * @return {@link Map} asociando cada candidato (o {@code null} para blanco) con su puntuación ponderada total (Double)
     * @throws RuntimeException si ocurre un error al procesar las sumatorias numéricas
     */
    public Map<Long, Double> obtenerResultadosPonderados(Long idEleccion) {
        Map<Long, Double> resultados = new HashMap<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT ID_CANDIDATO, SUM(" + pesoColumn(conn) + ") AS TOTAL_PONDERADO FROM Votos " +
                     "WHERE ID_ELECCION = ? GROUP BY ID_CANDIDATO")) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Long idCandidato = rs.getLong("ID_CANDIDATO");
                    resultados.put(rs.wasNull() ? null : idCandidato, rs.getDouble("TOTAL_PONDERADO"));
                }
                return resultados;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotoRepository.obtenerResultadosPonderados - idEleccion: " + idEleccion, e);
        }
    }

    /**
     * Devuelve el desglose estadístico de votación agrupado por los roles de los sufragantes.
     *
     * <p><em>Pendiente de implementación:</em> Reservado para futures extensiones del módulo de métricas.
     *
     * @param idEleccion identificador de la elección
     * @return un mapa vacío listo para albergar el desglose proyectado por rol
     */
    public Map<String, Integer> obtenerResultadosPorRol(Long idEleccion) {
        return new HashMap<>();
    }

    /**
     * Estructura la proyección base de columnas SQL inyectando nombres dinámicos.
     */
    private String selectBase(Connection conn) throws SQLException {
        return "SELECT " + idColumn(conn) + " AS ID_VOTO_ALIAS, FECHA_HORA, " +
                pesoColumn(conn) + " AS PESO_VOTO_ALIAS, ID_ELECCION, ID_CANDIDATO FROM Votos";
    }

    /**
     * Inspecciona la base de datos para determinar si el campo llave es {@code ID_VOTO} o {@code ID_VOTOS}.
     */
    private String idColumn(Connection conn) throws SQLException {
        return columnExists(conn, "ID_VOTO") ? "ID_VOTO" : "ID_VOTOS";
    }

    /**
     * Inspecciona la base de datos para determinar si el campo de peso es {@code PESO_VOTO_APLICADO} o {@code PESOVOTO_APLICADO}.
     */
    private String pesoColumn(Connection conn) throws SQLException {
        return columnExists(conn, "PESO_VOTO_APLICADO") ? "PESO_VOTO_APLICADO" : "PESOVOTO_APLICADO";
    }

    /**
     * Consulta el catálogo del esquema actual en Oracle para validar la existencia física de una columna.
     *
     * <p>Garantiza que el repositorio no falle con excepciones críticas de pánico por enlaces de nombres inválidos
     * en entornos multi-versión o migraciones parciales de tablas.
     *
     * @param conn conexión activa a la base de datos
     * @param columnName nombre textual exacto de la columna a verificar
     * @return {@code true} si la columna existe en la tabla 'VOTOS'; {@code false} en caso contrario
     * @throws SQLException si ocurre una excepción al consultar las tablas del diccionario del sistema (USER_TAB_COLUMNS)
     */
    private boolean columnExists(Connection conn, String columnName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'VOTOS' AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

}