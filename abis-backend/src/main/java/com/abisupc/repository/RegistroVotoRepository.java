package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.RegistroVoto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para la tabla {@code Registro_votos} en Oracle.
 *
 * <p>Gestiona la bitácora de auditoría que certifica la participación de los ciudadanos
 * en el certamen electoral. Este componente actúa como un mecanismo de control de concurrencia y
 * prevención de fraude, impidiendo el doble sufragio de un votante dentro de una misma elección.
 *
 * <p>Por motivos de seguridad transaccional, este repositorio es de <strong>solo lectura</strong>
 * desde la capa de Java; las mutaciones físicas están restringidas.
 *
 * <p>Tabla Oracle: {@code Registro_votos}
 * <ul>
 * <li>{@code ID_REGISTRO} — PK numérica autogenerada que identifica la transacción de participación.</li>
 * <li>{@code FECHA_HORA} — Estampa de tiempo exacta en que se radicó la participación en las mesas.</li>
 * <li>{@code IDENTIFICACION} — Cadena identificadora única de la cédula del votante.</li>
 * <li>{@code ID_PUESTO} — FK descriptiva de la sede física o mesa técnica donde votó.</li>
 * <li>{@code ID_ELECCION} — FK que liga el sufragio con una jornada electoral específica.</li>
 * </ul>
 */
public class RegistroVotoRepository implements Repository<RegistroVoto> {

    /**
     * Transforma una fila del {@link ResultSet} activo en un objeto de dominio {@link RegistroVoto}.
     *
     * <p>Realiza la conversión segura del tipo {@link Timestamp} nativo de Oracle hacia el tipo
     * {@link java.time.LocalDateTime} utilizado por las API de persistencia modernas de Java.
     *
     * @param rs cursor posicionado en la fila vigente a mapear
     * @return objeto {@link RegistroVoto} mapeado con los atributos de la base de datos
     * @throws SQLException si ocurre un error o inconsistencia en los nombres de las columnas
     */
    private RegistroVoto mapRow(ResultSet rs) throws SQLException {
        RegistroVoto registro = new RegistroVoto();
        Timestamp fechaHora = rs.getTimestamp("FECHA_HORA");

        registro.setId(rs.getLong("ID_REGISTRO"));
        registro.setFechaHora(fechaHora != null ? fechaHora.toLocalDateTime() : null);
        registro.setIdentificacion(rs.getString("IDENTIFICACION"));
        registro.setIdPuesto(rs.getLong("ID_PUESTO"));
        registro.setIdEleccion(rs.getLong("ID_ELECCION"));
        return registro;
    }

    /**
     * Busca el registro de participación de un votante por su identificador primario.
     *
     * @param id clave primaria del registro en {@code Registro_votos}
     * @return {@link Optional} conteniendo el registro si existe, o vacío en caso contrario
     * @throws RuntimeException si se produce una anomalía en el canal de datos JDBC
     */
    @Override
    public Optional<RegistroVoto> findById(Long id) {
        String sql = "SELECT ID_REGISTRO, FECHA_HORA, IDENTIFICACION, ID_PUESTO, ID_ELECCION " +
                "FROM Registro_votos WHERE ID_REGISTRO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.findById - id: " + id, e);
        }
    }

    /**
     * Recupera el histórico total de asistencias y participaciones electorales.
     *
     * @return lista estructurada con todos los registros encontrados; nunca retorna {@code null}
     * @throws RuntimeException si se genera un error imprevisto al consultar el cursor Oracle
     */
    @Override
    public List<RegistroVoto> findAll() {
        String sql = "SELECT ID_REGISTRO, FECHA_HORA, IDENTIFICACION, ID_PUESTO, ID_ELECCION " +
                "FROM Registro_votos ORDER BY ID_REGISTRO";
        List<RegistroVoto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapRow(rs));
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.findAll", e);
        }
    }

    /**
     * Operación denegada de manera explícita.
     *
     * <p>El asiento de participación ciudadana no se crea mediante sentencias directas de inserción (DML).
     * Su generación es delegada al procedimiento almacenado transaccional encapsulado {@code prc_registrar_voto}.
     *
     * @param entity entidad registro de voto a crear
     * @throws UnsupportedOperationException de forma invariable al invocarse este método
     */
    @Override
    public void save(RegistroVoto entity) {
        throw new UnsupportedOperationException("El registro de participacion solo se crea mediante prc_registrar_voto");
    }

    /**
     * Operación denegada de manera explícita.
     *
     * <p>Garantiza el principio de inmutabilidad y transparencia electoral. Las bitácoras de sufragio
     * asentadas no están sujetas a ediciones o alteraciones.
     *
     * @param entity entidad con las modificaciones a aplicar
     * @throws UnsupportedOperationException de forma invariable al invocarse este método
     */
    @Override
    public void update(RegistroVoto entity) {
        throw new UnsupportedOperationException("El registro de participacion es inmutable y no se actualiza desde Java");
    }

    /**
     * Operación denegada de manera explícita.
     *
     * <p>Protección contra la alteración maliciosa del censo de sufragantes activos. Ningún registro
     * puede ser eliminado físicamente del sistema relacional.
     *
     * @param id identificador del registro a remover
     * @throws UnsupportedOperationException de forma invariable al invocarse este método
     */
    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("El registro de participacion es inmutable y no se elimina desde Java");
    }

    /**
     * Evalúa expeditamente si un ciudadano ya ejerció su derecho al sufragio en una elección determinada.
     *
     * <p>Este método es crítico para la lógica de validación previa en los terminales de votación.
     *
     * @param identificacion documento de identidad del ciudadano
     * @param idEleccion identificador del proceso electoral concurrente
     * @return {@code true} si se localiza un registro previo que confirme su participación; {@code false} en caso contrario
     * @throws RuntimeException si falla la ejecución de la consulta de agregación por índice
     */
    public boolean yaVoto(String identificacion, Long idEleccion) {
        String sql = "SELECT COUNT(*) FROM Registro_votos WHERE IDENTIFICACION = ? AND ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ps.setLong(2, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.yaVoto - identificacion: " +
                    identificacion + ", idEleccion: " + idEleccion, e);
        }
    }

    /**
     * Recupera en orden cronológico inverso todos los registros de participación electoral para una elección específica.
     *
     * @param idEleccion identificador de la elección objeto de la consulta
     * @return lista de registros correspondientes a la elección parametrizada
     * @throws RuntimeException si se interrumpe la consulta relacional en el motor Oracle
     */
    public List<RegistroVoto> findByEleccion(Long idEleccion) {
        String sql = "SELECT ID_REGISTRO, FECHA_HORA, IDENTIFICACION, ID_PUESTO, ID_ELECCION " +
                "FROM Registro_votos WHERE ID_ELECCION = ? ORDER BY FECHA_HORA DESC";
        List<RegistroVoto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.findByEleccion - idEleccion: " + idEleccion, e);
        }
    }

    /**
     * Obtiene el histórico completo de participaciones de un único ciudadano a lo largo del tiempo.
     *
     * @param identificacion número del documento de identidad del votante
     * @return lista ordenada con los registros cronológicos del ciudadano
     * @throws RuntimeException si ocurre una falla en el aislamiento de los datos relacionales
     */
    public List<RegistroVoto> findByIdentificacion(String identificacion) {
        String sql = "SELECT ID_REGISTRO, FECHA_HORA, IDENTIFICACION, ID_PUESTO, ID_ELECCION " +
                "FROM Registro_votos WHERE IDENTIFICACION = ? ORDER BY FECHA_HORA DESC";
        List<RegistroVoto> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.findByIdentificacion - identificacion: " + identificacion, e);
        }
    }

    /**
     * Localiza con precisión el registro de asistencia de un sufragante específico para una elección determinada.
     *
     * @param identificacion documento de identidad del ciudadano
     * @param idEleccion identificador único de la jornada electoral concurrente
     * @return {@link Optional} encapsulando la participación exacta si existiese; vacío en caso de no haber sufragado
     * @throws RuntimeException si ocurre una excepción al procesar la sentencia relacional
     */
    public Optional<RegistroVoto> findByIdentificacionEleccion(String identificacion, Long idEleccion) {
        String sql = "SELECT ID_REGISTRO, FECHA_HORA, IDENTIFICACION, ID_PUESTO, ID_ELECCION " +
                "FROM Registro_votos WHERE IDENTIFICACION = ? AND ID_ELECCION = ? ORDER BY FECHA_HORA DESC";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ps.setLong(2, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.findByIdentificacionEleccion - identificacion: " +
                    identificacion + ", idEleccion: " + idEleccion, e);
        }
    }

    /**
     * Totaliza cuantitativamente la afluencia de sufragantes concurrentes que se han presentado
     * en un puesto físico específico para una determinada elección.
     *
     * @param idPuesto identificador físico de las instalaciones o mesas asignadas
     * @param idEleccion identificador del proceso electoral en curso
     * @return sumatoria total del número de electores que han completado su ciclo en dicho punto
     * @throws RuntimeException si el motor relacional falla al procesar la función agregada
     */
    public int countByPuesto(Long idPuesto, Long idEleccion) {
        String sql = "SELECT COUNT(*) FROM Registro_votos WHERE ID_PUESTO = ? AND ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idPuesto);
            ps.setLong(2, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en RegistroVotoRepository.countByPuesto - idPuesto: " +
                    idPuesto + ", idEleccion: " + idEleccion, e);
        }
    }
}
