package com.abisupc.repository;

import com.abisupc.config.AppConfig;
import com.abisupc.model.EstadoVotante;
import com.abisupc.model.Votante;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para la tabla {@code VOTANTES} en Oracle.
 *
 * <p>Gestiona el censo electoral y la información de los ciudadanos habilitados para votar.
 * Realiza un acoplamiento mediante {@code LEFT JOIN} con la tabla {@code BIOMETRIA_VOTANTES}
 * para determinar dinámicamente si el votante cuenta con un registro biométrico activo,
 * centralizando el estado de autenticación en las consultas de lectura.
 *
 * <p>Tabla Oracle Principal: {@code VOTANTES}
 * <ul>
 * <li>{@code IDENTIFICACION} — PK primaria basada en el documento de identidad (String)</li>
 * <li>{@code CORREO} — Correo electrónico único del ciudadano</li>
 * <li>{@code ESTADO_VOTO} — Estado del flujo de votación (ej. PENDIENTE, VOTÓ)</li>
 * <li>{@code ID_ROL} — FK que asocia al votante con sus permisos en elecciones</li>
 * <li>{@code ID_PUESTO} — FK hacia el puesto de votación asignado</li>
 * <li>{@code QR_CEDULA} — Hash o cadena del código QR de la cédula física</li>
 * </ul>
 */
public class VotanteRepository implements Repository<Votante> {
    private static final String SELECT_BASE = """
            SELECT v.IDENTIFICACION, v.CORREO, v.PRIMER_NOMBRE, v.SEGUNDO_NOMBRE,
                   v.PRIMER_APELLIDO, v.SEGUNDO_APELLIDO, v.ESTADO_VOTO, v.FOTO_URL,
                   v.FECHA_CONSENTIMIENTO, v.FECHA_NACIMIENTO, v.ID_ROL, v.ID_PUESTO, v.QR_CEDULA,
                   CASE WHEN bv.ACTIVO = 'S' THEN 1 ELSE 0 END AS BIOMETRICO
            FROM Votantes v
            LEFT JOIN BIOMETRIA_VOTANTES bv ON v.IDENTIFICACION = bv.IDENTIFICACION
            """;
    /**
     * Consulta base reutilizable para la lectura de votantes.
     * * <p>Integra la verificación biométrica transformando el indicador alfabético
     * ({@code 'S'/ 'N'}) en un entero binario ({@code 1 / 0}) ejecutable por el mapeador.
     */
    private static final String SELECT_BASE = """
            SELECT v.IDENTIFICACION, v.CORREO, v.PRIMER_NOMBRE, v.SEGUNDO_NOMBRE,
                   v.PRIMER_APELLIDO, v.SEGUNDO_APELLIDO, v.ESTADO_VOTO, v.FOTO_URL,
                   v.FECHA_CONSENTIMIENTO, v.FECHA_NACIMIENTO, v.ID_ROL, v.ID_PUESTO, v.QR_CEDULA,
                   CASE WHEN bv.ACTIVO = 'S' THEN 1 ELSE 0 END AS BIOMETRICO
            FROM Votantes v
            LEFT JOIN BIOMETRIA_VOTANTES bv ON v.IDENTIFICACION = bv.IDENTIFICACION
            """;

    /**
     * Convierte una fila del {@link ResultSet} en un objeto {@link Votante}.
     *
     * <p>Centraliza la extracción de datos relacionales asegurando que cualquier cambio
     * en el esquema de columnas de la base de datos impacte a un único método.
     *
     * @param rs cursor posicionado en la fila a mapear
     * @return objeto {@link Votante} construido con los datos de la fila actual
     * @throws SQLException si alguna columna mapeada no coincide con la proyección SQL
     */
    private Votante mapRow(ResultSet rs) throws SQLException {
        Votante votante = new Votante();
        votante.setIdentificacion(rs.getString("IDENTIFICACION"));
        votante.setCorreo(rs.getString("CORREO"));
        votante.setPrimerNombre(rs.getString("PRIMER_NOMBRE"));
        votante.setSegundoNombre(rs.getString("SEGUNDO_NOMBRE"));
        votante.setPrimerApellido(rs.getString("PRIMER_APELLIDO"));
        votante.setSegundoApellido(rs.getString("SEGUNDO_APELLIDO"));
        votante.setEstadoVoto(rs.getString("ESTADO_VOTO"));
        votante.setFotoUrl(rs.getString("FOTO_URL"));
        votante.setFechaConsentimiento(rs.getTimestamp("FECHA_CONSENTIMIENTO"));
        votante.setFechaNacimiento(rs.getDate("FECHA_NACIMIENTO"));
        votante.setIdRol(rs.getLong("ID_ROL"));
        votante.setIdPuesto(rs.getLong("ID_PUESTO"));
        votante.setQrCedula(rs.getString("QR_CEDULA"));
        votante.setBiometrico(rs.getInt("BIOMETRICO") == 1);
        return votante;
    }

    /**
     * Busca un votante utilizando la conversión numérica de su identificador primario.
     *
     * <p>Delega internamente la búsqueda a {@link #findByIdentificacion(String)}
     * garantizando la reutilización del diseño y consistencia en consultas por índice.
     *
     * @param id identificador numérico convertido a cadena
     * @return {@link Optional} con el votante si existe, o vacío en caso contrario
     */
    @Override
    public Optional<Votante> findById(Long id) {
        return findByIdentificacion(String.valueOf(id));
    }

    /**
     * Recupera todos los votantes del censo electoral ordenados alfabéticamente.
     *
     * @return lista con todos los votantes registrados; nunca devuelve {@code null}
     * @throws RuntimeException si se genera un fallo de comunicación o sintaxis en Oracle
     */
    @Override
    public List<Votante> findAll() {
        String sql = SELECT_BASE + " ORDER BY v.PRIMER_APELLIDO, v.PRIMER_NOMBRE";
        List<Votante> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapRow(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findAll", e);
        }
    }

    /**
     * Inserta un nuevo votante en la base de datos.
     *
     * <p>Si el {@code estadoVoto} provisto por la entidad es nulo, se predetermina
     * de forma automática al estado {@code PENDIENTE} utilizando {@link EstadoVotante}.
     *
     * @param entity objeto votante con la información requerida para el registro
     * @throws RuntimeException si se viola la restricción de unicidad de la PK (ORA-00001),
     * indicando que el documento ya se encuentra registrado
     */
    @Override
    public void save(Votante entity) {
        String sql = "INSERT INTO Votantes (IDENTIFICACION, CORREO, PRIMER_NOMBRE, SEGUNDO_NOMBRE, " +
                "PRIMER_APELLIDO, SEGUNDO_APELLIDO, ESTADO_VOTO, FOTO_URL, FECHA_CONSENTIMIENTO, FECHA_NACIMIENTO, ID_ROL, ID_PUESTO, QR_CEDULA) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getIdentificacion());
            ps.setString(2, entity.getCorreo());
            ps.setString(3, entity.getPrimerNombre());
            ps.setString(4, entity.getSegundoNombre());
            ps.setString(5, entity.getPrimerApellido());
            ps.setString(6, entity.getSegundoApellido());
            ps.setString(7, entity.getEstadoVoto() != null ? entity.getEstadoVoto() : EstadoVotante.PENDIENTE.name());
            ps.setString(8, entity.getFotoUrl());
            ps.setTimestamp(9, entity.getFechaConsentimiento());
            ps.setDate(10, entity.getFechaNacimiento());
            ps.setLong(11, entity.getIdRol());
            ps.setLong(12, entity.getIdPuesto());
            ps.setString(13, entity.getQrCedula());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1) {
                throw new RuntimeException("Ya existe un votante con la identificacion: " + entity.getIdentificacion(), e);
            }
            throw new RuntimeException("Error en VotanteRepository.save", e);
        }
    }

    @Override
    public void update(Votante entity) {
        String sql = "UPDATE Votantes SET CORREO = ?, PRIMER_NOMBRE = ?, SEGUNDO_NOMBRE = ?, " +
                "PRIMER_APELLIDO = ?, SEGUNDO_APELLIDO = ?, FOTO_URL = ?, " +
                "FECHA_CONSENTIMIENTO = ?, FECHA_NACIMIENTO = ?, ID_ROL = ?, ID_PUESTO = ?, QR_CEDULA = ? WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getCorreo());
            ps.setString(2, entity.getPrimerNombre());
            ps.setString(3, entity.getSegundoNombre());
            ps.setString(4, entity.getPrimerApellido());
            ps.setString(5, entity.getSegundoApellido());
            ps.setString(6, entity.getFotoUrl());
            ps.setTimestamp(7, entity.getFechaConsentimiento());
            ps.setDate(8, entity.getFechaNacimiento());
            ps.setLong(9, entity.getIdRol());
            ps.setLong(10, entity.getIdPuesto());
            ps.setString(11, entity.getQrCedula());
            ps.setString(12, entity.getIdentificacion());
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontro el votante con identificacion: " + entity.getIdentificacion());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.update - identificacion: " + entity.getIdentificacion(), e);
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getIdentificacion());
            ps.setString(2, entity.getCorreo());
            ps.setString(3, entity.getPrimerNombre());
            ps.setString(4, entity.getSegundoNombre());
            ps.setString(5, entity.getPrimerApellido());
            ps.setString(6, entity.getSegundoApellido());
            ps.setString(7, entity.getEstadoVoto() != null ? entity.getEstadoVoto() : EstadoVotante.PENDIENTE.name());
            ps.setString(8, entity.getFotoUrl());
            ps.setTimestamp(9, entity.getFechaConsentimiento());
            ps.setDate(10, entity.getFechaNacimiento());
            ps.setLong(11, entity.getIdRol());
            ps.setLong(12, entity.getIdPuesto());
            ps.setString(13, entity.getQrCedula());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1) {
                throw new RuntimeException("Ya existe un votante con la identificacion: " + entity.getIdentificacion(), e);
            }
            throw new RuntimeException("Error en VotanteRepository.save", e);
        }
    }

    /**
     * Actualiza los datos demográficos y de configuración de un votante existente.
     *
     * <p>Nota de diseño: Este método modifica datos del perfil mas **no** altera el estado
     * del voto por seguridad transaccional. Si el registro no es localizado por su PK,
     * se genera una excepción explícita.
     *
     * @param entity votante con las modificaciones actualizadas; debe portar una identificación válida
     * @throws RuntimeException si no se encuentra ningún registro con la identificación suministrada
     */
    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM Votantes WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(id));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.delete - identificacion: " + id, e);
    public void update(Votante entity) {
        String sql = "UPDATE Votantes SET CORREO = ?, PRIMER_NOMBRE = ?, SEGUNDO_NOMBRE = ?, " +
                "PRIMER_APELLIDO = ?, SEGUNDO_APELLIDO = ?, FOTO_URL = ?, " +
                "FECHA_CONSENTIMIENTO = ?, FECHA_NACIMIENTO = ?, ID_ROL = ?, ID_PUESTO = ?, QR_CEDULA = ? WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getCorreo());
            ps.setString(2, entity.getPrimerNombre());
            ps.setString(3, entity.getSegundoNombre());
            ps.setString(4, entity.getPrimerApellido());
            ps.setString(5, entity.getSegundoApellido());
            ps.setString(6, entity.getFotoUrl());
            ps.setTimestamp(7, entity.getFechaConsentimiento());
            ps.setDate(8, entity.getFechaNacimiento());
            ps.setLong(9, entity.getIdRol());
            ps.setLong(10, entity.getIdPuesto());
            ps.setString(11, entity.getQrCedula());
            ps.setString(12, entity.getIdentificacion());
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontro el votante con identificacion: " + entity.getIdentificacion());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.update - identificacion: " + entity.getIdentificacion(), e);
        }
    }

    /**
     * Elimina del censo a un votante a través de su clave primaria.
     *
     * @param id documento de identidad mapeado temporalmente como numérico largo
     * @throws RuntimeException si ocurre una falla en la instrucción o violación de integridad
     */
    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM Votantes WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(id));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.delete - identificacion: " + id, e);
        }
    }

    /**
     * Busca un votante de manera exacta a través de su documento de identidad.
     *
     * @param identificacion número del documento en formato String
     * @return {@link Optional} encapsulando al votante si es localizado; vacío si no existe
     * @throws RuntimeException en caso de error crítico en la capa de datos
     */
    public Optional<Votante> findByIdentificacion(String identificacion) {
        String sql = SELECT_BASE + " WHERE v.IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByIdentificacion - identificacion: " + identificacion, e);
        }
    }

    /**
     * Busca un votante mediante su dirección de correo electrónico institucional o personal.
     *
     * @param correo dirección de correo electrónico exacta
     * @return {@link Optional} con el votante correspondiente o vacío si no hay coincidencia
     * @throws RuntimeException en caso de fallas imprevistas del controlador JDBC
     */
    public Optional<Votante> findByCorreo(String correo) {
        String sql = SELECT_BASE + " WHERE v.CORREO = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, correo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByCorreo - correo: " + correo, e);
        }
    }

    /**
     * Localiza un votante interpretando el código bidimensional descodificado de su documento.
     *
     * @param qrCedula cadena representativa extraída del QR del documento
     * @return {@link Optional} con el registro del votante si coincide el código QR
     * @throws RuntimeException si se interrumpe la consulta SQL
     */
    public Optional<Votante> findByQrCedula(String qrCedula) {
        String sql = SELECT_BASE + " WHERE v.QR_CEDULA = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, qrCedula);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByQrCedula", e);
        }
    }

    /**
     * Recupera la lista de votantes vinculados a un perfil o rol electoral específico.
     *
     * @param idRol identificador del rol requerido
     * @return lista de votantes que ejercen dicho rol; estructurada y ordenada por apellidos
     * @throws RuntimeException si se produce un fallo de conexión a la base de datos
     */
    public List<Votante> findByIdRol(Long idRol) {
        String sql = SELECT_BASE + " WHERE v.ID_ROL = ? ORDER BY v.PRIMER_APELLIDO, v.PRIMER_NOMBRE";
        List<Votante> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idRol);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByIdRol - idRol: " + idRol, e);
        }
    }

    /**
     * Obtiene todos los votantes asignados a una misma sede o puesto físico de votación.
     *
     * @param idPuesto identificador de la ubicación física o mesa principal
     * @return lista de votantes adscritos al puesto físico correspondiente
     * @throws RuntimeException si ocurre una anomalía durante la lectura del cursor
     */
    public List<Votante> findByIdPuesto(Long idPuesto) {
        String sql = SELECT_BASE + " WHERE v.ID_PUESTO = ? ORDER BY v.PRIMER_APELLIDO, v.PRIMER_NOMBRE";
        List<Votante> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idPuesto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.findByIdPuesto - idPuesto: " + idPuesto, e);
        }
    }

    /**
     * Consulta los votantes idóneos para activar protocolos de contingencia en una elección.
     *
     * <p>Filtra estrictamente los ciudadanos asociados a la elección vía la tabla
     * asociativa {@code Eleccion_roles}, cuyo estado se mantenga estrictamente en
     * {@code 'PENDIENTE'} y dispongan de una dirección de correo válida para notificaciones urgentes.
     *
     * @param idEleccion identificador de la jornada electoral activa
     * @return lista filtrada de votantes aptos para contingencia ordenados alfabéticamente
     * @throws RuntimeException si el motor relacional falla al procesar las uniones
     */
    public List<Votante> findHabilitadosParaContingencia(Long idEleccion) {
        String sql = """
                SELECT v.IDENTIFICACION, v.CORREO, v.PRIMER_NOMBRE, v.SEGUNDO_NOMBRE,
                       v.PRIMER_APELLIDO, v.SEGUNDO_APELLIDO, v.ESTADO_VOTO, v.FOTO_URL,
                       v.FECHA_CONSENTIMIENTO, v.FECHA_NACIMIENTO, v.ID_ROL, v.ID_PUESTO, v.QR_CEDULA,
                       CASE WHEN bv.ACTIVO = 'S' THEN 1 ELSE 0 END AS BIOMETRICO
                FROM Votantes v
                LEFT JOIN BIOMETRIA_VOTANTES bv ON v.IDENTIFICACION = bv.IDENTIFICACION
                INNER JOIN Eleccion_roles er ON er.id_rol = v.id_rol
                WHERE er.id_eleccion = ?
                  AND UPPER(v.ESTADO_VOTO) = 'PENDIENTE'
                  AND v.CORREO IS NOT NULL
                ORDER BY v.PRIMER_APELLIDO, v.PRIMER_NOMBRE
                """;
        List<Votante> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error consultando votantes habilitados para contingencia", e);
        }
    }

            throw new RuntimeException("Error consulting votantes habilitados para contingencia", e);
        }
    }

    /**
     * Recupera el censo total de votantes vinculados a una elección específica.
     *
     * <p>Utiliza un {@code INNER JOIN} implícito con {@code Eleccion_roles} para aislar
     * exclusivamente los registros adscritos a los roles asignados a la jornada indicada.
     *
     * @param idEleccion identificador único de la elección
     * @return lista de votantes adscritos al proceso electoral parametrizado
     * @throws RuntimeException si la base de datos devuelve un fallo en la ejecución
     */
    public List<Votante> findByEleccion(Long idEleccion) {
        String sql = """
                SELECT v.IDENTIFICACION, v.CORREO, v.PRIMER_NOMBRE, v.SEGUNDO_NOMBRE,
                       v.PRIMER_APELLIDO, v.SEGUNDO_APELLIDO, v.ESTADO_VOTO, v.FOTO_URL,
                       v.FECHA_CONSENTIMIENTO, v.FECHA_NACIMIENTO, v.ID_ROL, v.ID_PUESTO, v.QR_CEDULA,
                       CASE WHEN bv.ACTIVO = 'S' THEN 1 ELSE 0 END AS BIOMETRICO
                FROM Votantes v
                LEFT JOIN BIOMETRIA_VOTANTES bv ON v.IDENTIFICACION = bv.IDENTIFICACION
                INNER JOIN Eleccion_roles er ON er.id_rol = v.id_rol
                WHERE er.id_eleccion = ?
                ORDER BY v.PRIMER_APELLIDO, v.PRIMER_NOMBRE
                """;
        List<Votante> lista = new ArrayList<>();
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEleccion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error consultando votantes por eleccion " + idEleccion, e);
        }
    }

    /**
     * Valida de manera rápida si un ciudadano se encuentra habilitado para ejercer el voto.
     *
     * <p>Un votante se asume habilitado si y sólo si su columna {@code ESTADO_VOTO}
     * coincide textualmente con el literal de {@link EstadoVotante#PENDIENTE}.
     *
     * @param identificacion número del documento del votante bajo escrutinio
     * @return {@code true} si el votante existe y su estado es PENDIENTE; {@code false} en cualquier otro caso
     * @throws RuntimeException si se presenta un error de conectividad JDBC
     */
    public boolean estaHabilitado(String identificacion) {
        String sql = "SELECT ESTADO_VOTO FROM Votantes WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && EstadoVotante.PENDIENTE.name().equals(rs.getString("ESTADO_VOTO"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.estaHabilitado - identificacion: " + identificacion, e);
        }
    }

    /**
     * Operación no soportada localmente.
     *
     * <p>Por directrices de auditoría y seguridad del sistema electoral, la mutación
     * del estado de votación debe ser efectuada estrictamente de forma centralizada
     * mediante procedimientos almacenados Oracle autorizados.
     *
     * @param identificacion número de documento del votante
     * @param estado nuevo estado a aplicar
     * @throws UnsupportedOperationException de forma invariable al invocarse este método
     */
    public void actualizarEstado(String identificacion, String estado) {
        throw new UnsupportedOperationException("El estado del votante se cambia mediante procedimientos Oracle autorizados");
    }

    /**
     * Modifica exclusivamente la URL de la fotografía de perfil o reconocimiento del votante.
     *
     * <p>Diseñado para flujos rápidos de enrolamiento o actualización en mesa. Si la
     * identificación suministrada no existe en el censo, abortará emitiendo una excepción.
     *
     * @param identificacion documento de identidad del ciudadano
     * @param fotoUrl ruta web o del storage con la nueva imagen cargada
     * @throws RuntimeException si no existe el registro de votante mapeado a la identificación
     */
    public void actualizarFoto(String identificacion, String fotoUrl) {
        String sql = "UPDATE Votantes SET FOTO_URL = ? WHERE IDENTIFICACION = ?";
        try (Connection conn = AppConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fotoUrl);
            ps.setString(2, identificacion);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new RuntimeException("No se encontro el votante con identificacion: " + identificacion);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en VotanteRepository.actualizarFoto - identificacion: " + identificacion, e);
        }
    }
}
}
