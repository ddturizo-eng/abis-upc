package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.Candidato;
import com.abisupc.model.CandidatoEleccion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para las tablas {@code CANDIDATOS} y {@code CANDIDATOS_ELECCION} en Oracle.
 *
 * <p>Gestiona la persistencia de las hojas de vida de los candidatos, sus respectivas
 * postulaciones a cargos de elección popular y el cálculo consolidado en tiempo real de los
 * sufragios obtenidos durante los escrutinios.
 *
 * <p>El componente incorpora un mecanismo automatizado de evolución de esquema que verifica
 * e inyecta la columna de contenido multimedia en caliente si el diccionario de la base de
 * datos no la registra inicialmente, evitando fallas de despliegue multi-versión.
 *
 * <p>Tablas Oracle Involucradas:
 * <ul>
 * <li>{@code CANDIDATOS} — Maestro de ciudadanos postulados. Usa la secuencia {@code seq_candidatos}.</li>
 * <li>{@code CANDIDATOS_ELECCION} — Tabla intermedia asociativa que vincula al candidato con una
 * elección específica, asignando su número de tarjetón electoral y el cargo al que aspira.</li>
 * </ul>
 */
public class CandidatoRepository implements Repository<Candidato> {

    /**
     * Recupera el listado completo de candidatos registrados en el maestro general.
     *
     * @return lista estructurada de candidatos ordenados por su clave primaria; nunca devuelve {@code null}
     * @throws RuntimeException si ocurre una excepción de comunicación JDBC o sintaxis SQL
     */
    @Override
    public List<Candidato> findAll() {
        String sql = "SELECT ID_CANDIDATO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, FOTO_URL FROM Candidatos ORDER BY ID_CANDIDATO";
        List<Candidato> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection()) {
            ensureFotoUrlColumn(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapCandidato(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.findAll", e);
        }
    }

    /**
     * Busca un candidato específico en el sistema por su identificador único.
     *
     * @param id clave primaria del candidato a localizar
     * @return {@link Optional} encapsulando la entidad del candidato si existe; un contenedor vacío en caso contrario
     * @throws RuntimeException si se interrumpe la conexión o falla el mapeo relacional
     */
    @Override
    public Optional<Candidato> findById(Long id) {
        String sql = "SELECT ID_CANDIDATO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, FOTO_URL FROM Candidatos WHERE ID_CANDIDATO = ?";
        try (Connection conn = AppConfig.getConnection()) {
            ensureFotoUrlColumn(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapCandidato(rs));
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.findById - id: " + id, e);
        }
    }

    /**
     * Registra un nuevo candidato de forma persistente y actualiza su identificador autogenerado.
     *
     * <p>Delega la inserción física a {@link #savePersona(Candidato)}, el cual recupera la clave
     * primaria asignada transaccionalmente por la secuencia del motor relacional.
     *
     * @param entity objeto candidato con la información demográfica a persistir
     */
    @Override
    public void save(Candidato entity) {
        Long id = savePersona(entity);
        entity.setId(id);
    }

    public Long savePersona(Candidato c) {
        String sql = "INSERT INTO Candidatos (ID_CANDIDATO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO, FOTO_URL) " +
                "VALUES (seq_candidatos.NEXTVAL, ?, ?, ?, ?, ?)";
        try (Connection conn = AppConfig.getConnection()) {
            ensureFotoUrlColumn(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, c.getPrimerNombre());
                ps.setString(2, c.getSegundoNombre());
                ps.setString(3, c.getPrimerApellido());
                ps.setString(4, c.getSegundoApellido());
                ps.setString(5, c.getFotoUrl());
                ps.executeUpdate();
            }
            try (PreparedStatement seq = conn.prepareStatement("SELECT seq_candidatos.CURRVAL FROM dual");
                 ResultSet rs = seq.executeQuery()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    c.setId(id);
                    return id;
                }
                throw new SQLException("No se pudo leer seq_candidatos.CURRVAL");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.savePersona", e);
        }
    }

    public void savePostulacion(Long idCandidato, Long idEleccion, Integer numeroCampania, String cargo) {
        validarNumeroCampaniaDisponible(idEleccion, numeroCampania, null);
        String sql = "INSERT INTO Candidatos_eleccion (ID_CANDIDATO, ID_ELECCION, NUMERO_CAMPANIA, CARGO) VALUES (?, ?, ?, ?)";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idCandidato);
            ps.setLong(2, idEleccion);
            ps.setInt(3, numeroCampania);
            ps.setString(4, cargo);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.savePostulacion", e);
        }
    }

    /**
     * Actualiza la información del perfil maestro de un candidato.
     *
     * <p>Utiliza la función {@code COALESCE} en la columna {@code FOTO_URL} para prevenir la pérdida
     * o sobreescritura accidental del enlace multimedia previo en caso de recibir un valor nulo.
     *
     * @param entity entidad candidato portando las actualizaciones y su respectivo ID primario
     * @throws RuntimeException si ocurre una anomalía durante la actualización en el servidor Oracle
     */
    @Override
    public void update(Candidato entity) {
        String sql = "UPDATE Candidatos SET PRIMER_NOMBRE = ?, SEGUNDO_NOMBRE = ?, PRIMER_APELLIDO = ?, SEGUNDO_APELLIDO = ?, FOTO_URL = COALESCE(?, FOTO_URL) WHERE ID_CANDIDATO = ?";
        try (Connection conn = AppConfig.getConnection()) {
            ensureFotoUrlColumn(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, entity.getPrimerNombre());
                ps.setString(2, entity.getSegundoNombre());
                ps.setString(3, entity.getPrimerApellido());
                ps.setString(4, entity.getSegundoApellido());
                ps.setString(5, entity.getFotoUrl());
                ps.setLong(6, entity.getId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.update - id: " + entity.getId(), e);
        }
    }

    /**
     * Modifica los atributos de la postulación activa de un candidato dentro de una contienda.
     *
     * <p>Valida la disponibilidad del número de tarjetón mediante exclusión del propio ID del candidato
     * para permitir que mantenga su número actual si solo se modifica el cargo o campos descriptivos.
     *
     * @param idCandidato identificador del candidato cuya postulación será editada
     * @param idEleccion elección donde se aplica la modificación
     * @param numeroCampania nuevo número de tarjetón asignado
     * @param cargo nuevo cargo objeto de postulación
     * @throws IllegalArgumentException si el nuevo número de campaña colisiona con otra postulación activa
     */
    public void updatePostulacion(Long idCandidato, Long idEleccion, Integer numeroCampania, String cargo) {
        validarNumeroCampaniaDisponible(idEleccion, numeroCampania, idCandidato);
        String sql = "UPDATE Candidatos_eleccion SET NUMERO_CAMPANIA = ?, CARGO = ? WHERE ID_CANDIDATO = ? AND ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, numeroCampania);
            ps.setString(2, cargo);
            ps.setLong(3, idCandidato);
            ps.setLong(4, idEleccion);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.updatePostulacion", e);
        }
    }

    /**
     * Recupera el censo total de candidatos postulados a una elección, consolidando el escrutinio de votos por participante.
     *
     * <p>Efectúa un acoplamiento mediante {@code LEFT JOIN} con una subconsulta de agregación agrupada por
     * candidato sobre la tabla {@code Votos}. Utiliza la función {@code NVL} para normalizar las ausencias de sufragio
     * devolviendo {@code 0} votos en lugar de nulos relacionales.
     *
     * @param idEleccion identificador único de la contienda electoral a escrutar
     * @return lista completa de proyecciones {@link CandidatoEleccion} ordenada jerárquicamente por cargo y tarjetón
     * @throws RuntimeException si falla la sincronización o cálculo del bloque de agregación SQL
     */
    public List<CandidatoEleccion> findByEleccion(Long idEleccion) {
        String sql = "SELECT ce.ID_CANDIDATO, ce.ID_ELECCION, ce.NUMERO_CAMPANIA, ce.CARGO, " +
                "c.PRIMER_NOMBRE, c.SEGUNDO_NOMBRE, c.PRIMER_APELLIDO, c.SEGUNDO_APELLIDO, c.FOTO_URL, " +
                "NVL(v.TOTAL_VOTOS, 0) AS VOTOS " +
                "FROM Candidatos_eleccion ce JOIN Candidatos c ON c.ID_CANDIDATO = ce.ID_CANDIDATO " +
                "LEFT JOIN (SELECT ID_CANDIDATO, ID_ELECCION, COUNT(*) AS TOTAL_VOTOS FROM Votos " +
                "WHERE ID_ELECCION = ? GROUP BY ID_CANDIDATO, ID_ELECCION) v " +
                "ON v.ID_CANDIDATO = ce.ID_CANDIDATO AND v.ID_ELECCION = ce.ID_ELECCION " +
                "WHERE ce.ID_ELECCION = ? ORDER BY ce.CARGO, ce.NUMERO_CAMPANIA";
        List<CandidatoEleccion> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection()) {
            ensureFotoUrlColumn(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, idEleccion);
                ps.setLong(2, idEleccion);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(mapRow(rs));
                    }
                    return lista;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.findByEleccion - idEleccion: " + idEleccion, e);
        }
    }

    /**
     * Verifica de manera ágil si un candidato ya cuenta con sufragios computados a su favor en una elección.
     *
     * <p>Esta validación es una salvaguarda de auditoría vital antes de autorizar modificaciones,
     * eliminaciones o descalificaciones de candidatos en procesos en marcha.
     *
     * @param idCandidato identificador del candidato
     * @param idEleccion identificador del proceso electoral
     * @return {@code true} si se detecta al menos un voto asociado; {@code false} si el contador es cero
     * @throws RuntimeException en caso de fallos técnicos en la capa de datos
     */
    public boolean tieneVotos(Long idCandidato, Long idEleccion) {
        String sql = "SELECT COUNT(*) FROM Votos WHERE ID_CANDIDATO = ? AND ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idCandidato);
            ps.setLong(2, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.tieneVotos", e);
        }
    }

    /**
     * Remueve el registro de postulación de un candidato en una elección determinada.
     *
     * <p>Nota: Elimina el enlace asociativo en {@code Candidatos_eleccion} sin alterar
     * la hoja de vida ni la información base del maestro de candidatos.
     *
     * @param idCandidato identificador del candidato cuya postulación se da de baja
     * @param idEleccion identificador de la elección afectada
     * @throws RuntimeException en caso de violaciones de integridad referencial
     */
    public void deletePostulacion(Long idCandidato, Long idEleccion) {
        String sql = "DELETE FROM Candidatos_eleccion WHERE ID_CANDIDATO = ? AND ID_ELECCION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idCandidato);
            ps.setLong(2, idEleccion);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.deletePostulacion", e);
        }
    }

    /**
     * Elimina de forma definitiva a un candidato del maestro general por su clave primaria.
     *
     * @param id identificador único del candidato a suprimir
     * @throws RuntimeException si el registro está referenciado activamente en tablas subordinadas
     * por restricciones FK sin cascada
     */
    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM Candidatos WHERE ID_CANDIDATO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.delete - id: " + id, e);
        }
    }

    /**
     * Valida si un número de tarjetón ya fue asignado en una elección específica.
     *
     * <p>Soporta una cláusula de exclusión condicional ({@code idExcluir}) para obviar al propio
     * candidato durante flujos transaccionales de edición/actualización de datos.
     *
     * @param idEleccion identificador de la jornada bajo escrutinio
     * @param numeroCampania número de campaña electoral que se pretende verificar
     * @param idExcluir identificador del candidato a omitir de la verificación, o {@code null} para validaciones globales
     * @throws IllegalArgumentException si la consulta detecta duplicidad del número de tarjetón
     */
    private void validarNumeroCampaniaDisponible(Long idEleccion, Integer numeroCampania, Long idExcluir) {
        String sql = "SELECT COUNT(*) FROM Candidatos_eleccion WHERE ID_ELECCION = ? AND NUMERO_CAMPANIA = ?";
        if (idExcluir != null) {
            sql += " AND ID_CANDIDATO != ?";
        }

        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            ps.setInt(2, numeroCampania);
            if (idExcluir != null) {
                ps.setLong(3, idExcluir);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new IllegalArgumentException("Número de campaña ya existe en esta elección");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en CandidatoRepository.validarNumeroCampaniaDisponible", e);
        }
    }

    private Candidato mapCandidato(ResultSet rs) throws SQLException {
        Candidato c = new Candidato();
        c.setId(rs.getLong("ID_CANDIDATO"));
        c.setPrimerNombre(rs.getString("PRIMER_NOMBRE"));
        c.setSegundoNombre(rs.getString("SEGUNDO_NOMBRE"));
        c.setPrimerApellido(rs.getString("PRIMER_APELLIDO"));
        c.setSegundoApellido(rs.getString("SEGUNDO_APELLIDO"));
        c.setFotoUrl(rs.getString("FOTO_URL"));
        return c;
    }

    private CandidatoEleccion mapRow(ResultSet rs) throws SQLException {
        CandidatoEleccion ce = new CandidatoEleccion();
        ce.setIdCandidato(rs.getLong("ID_CANDIDATO"));
        ce.setIdEleccion(rs.getLong("ID_ELECCION"));
        ce.setNumeroCampania(rs.getInt("NUMERO_CAMPANIA"));
        ce.setCargo(rs.getString("CARGO"));
        ce.setPrimerNombre(rs.getString("PRIMER_NOMBRE"));
        ce.setSegundoNombre(rs.getString("SEGUNDO_NOMBRE"));
        ce.setPrimerApellido(rs.getString("PRIMER_APELLIDO"));
        ce.setSegundoApellido(rs.getString("SEGUNDO_APELLIDO"));
        ce.setFotoUrl(rs.getString("FOTO_URL"));
        ce.setVotos(rs.getInt("VOTOS"));
        return ce;
    }

    private void ensureFotoUrlColumn(Connection conn) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'CANDIDATOS' AND COLUMN_NAME = 'FOTO_URL'";
        try (PreparedStatement ps = conn.prepareStatement(checkSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("ALTER TABLE Candidatos ADD FOTO_URL VARCHAR2(500)")) {
            ps.executeUpdate();
        }
    }
}
