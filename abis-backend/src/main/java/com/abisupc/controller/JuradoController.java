package com.abisupc.controller;

import com.abisupc.config.AppConfig;
import com.abisupc.dto.ApiResponse;
import com.abisupc.repository.JuradoRepository;
import com.abisupc.util.OracleErrorHandler;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.DatabaseMetaData;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Endpoints administrativos para mesas y jurados electorales.
 */
public class JuradoController {

    private static final JuradoRepository juradoRepo = new JuradoRepository();

    private static final Map<Long, String> ROLES = Map.of(
            1L, "ESTUDIANTE",
            2L, "DOCENTE",
            3L, "EGRESADO",
            4L, "ADMINISTRATIVO"
    );

    public static void getAll(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            ctx.json(ApiResponse.success(juradosCompletos(conn, null)));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void mesas(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            ctx.json(ApiResponse.success(mesasCompletas(conn, null)));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void resumenAsignacion(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            Map<?, ?> configuracion = map(body.get("configuracion"));
            Map<?, ?> poolConfig = map(configuracion.get("poolConfig"));
            Map<?, ?> distribucionConfig = map(configuracion.get("distribucionConfig"));
            Long idEleccion = optionalLong(body.get("idEleccion"));

            List<Map<String, Object>> pool = poolElegibles(conn, poolConfig, idEleccion);
            List<Map<String, Object>> mesas = mesasCompletas(conn, null);
            int mesasConfiguradas = mesas.size();
            int puestosConfigurados = puestos(conn).size();
            int baseMesas = mesasConfiguradas > 0 ? mesasConfiguradas : puestosConfigurados;
            int juradosPorMesa = intValue(distribucionConfig.get("valorFijo"), 3);
            int juradosRequeridos = calcularTotalRequerido(distribucionConfig, pool.size(), baseMesas);
            int asignables = Math.min(pool.size(), juradosRequeridos);
            int cobertura = juradosRequeridos > 0 ? Math.round((asignables * 100f) / juradosRequeridos) : 0;
            int completas = juradosPorMesa > 0 ? Math.min(baseMesas, asignables / juradosPorMesa) : 0;
            int restantes = Math.max(0, asignables - (completas * Math.max(juradosPorMesa, 1)));
            int incompletas = restantes > 0 ? 1 : 0;
            int criticas = Math.max(0, baseMesas - completas - incompletas);

            Map<String, Integer> roles = new LinkedHashMap<>();
            roles.put("ESTUDIANTE", 0);
            roles.put("DOCENTE", 0);
            roles.put("ADMINISTRATIVO", 0);
            roles.put("EGRESADO", 0);
            for (Map<String, Object> votante : pool) {
                String rol = String.valueOf(votante.getOrDefault("rol", ""));
                roles.put(rol, roles.getOrDefault(rol, 0) + 1);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("pool", pool.size());
            response.put("mesas", baseMesas);
            response.put("mesasRegistradas", mesasConfiguradas);
            response.put("puestos", puestosConfigurados);
            response.put("juradosPorMesa", juradosPorMesa);
            response.put("juradosRequeridos", juradosRequeridos);
            response.put("asignables", asignables);
            response.put("cobertura", cobertura);
            response.put("completas", completas);
            response.put("incompletas", incompletas);
            response.put("criticas", criticas);
            response.put("conflictos", Math.max(0, juradosRequeridos - pool.size()));
            response.put("roles", roles);
            ctx.json(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void poolElegible(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            Map<?, ?> configuracion = map(body.get("configuracion"));
            Long idEleccion = optionalLong(body.get("idEleccion"));
            ctx.json(ApiResponse.success(poolElegibles(conn, map(configuracion.get("poolConfig")), idEleccion)));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void mesaDetalle(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            Long id = Long.parseLong(ctx.pathParam("id"));
            List<Map<String, Object>> mesas = mesasCompletas(conn, id);
            if (mesas.isEmpty()) {
                ctx.status(404).json(ApiResponse.error("Mesa no encontrada"));
                return;
            }
            ctx.json(ApiResponse.success(mesas.get(0)));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void crearMesa(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            Long id = insertarMesa(conn, parseMesa(body));
            ctx.status(201).json(ApiResponse.success(Map.of("idMesa", id)));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void editarMesa(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> mesa = parseMesa(ctx.bodyAsClass(Map.class));
            String sql = "UPDATE Mesa_jurados SET HORA_INGRESO = ?, HORA_SALIDA = ?, CARGO = ?, " +
                    mesaPuestoColumn(conn) + " = ?, ID_ELECCION = ? WHERE ID_MESA = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setTimestamp(1, (Timestamp) mesa.get("horaIngreso"));
                ps.setTimestamp(2, (Timestamp) mesa.get("horaSalida"));
                ps.setString(3, (String) mesa.get("cargo"));
                ps.setLong(4, (Long) mesa.get("idPuesto"));
                setLongOrNull(ps, 5, (Number) mesa.get("idEleccion"));
                ps.setLong(6, id);
                if (ps.executeUpdate() == 0) {
                    ctx.status(404).json(ApiResponse.error("Mesa no encontrada"));
                    return;
                }
            }
            ctx.json(ApiResponse.success("Mesa actualizada"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void eliminarMesa(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            Long id = Long.parseLong(ctx.pathParam("id"));
            boolean force = "true".equalsIgnoreCase(ctx.queryParam("force"));
            int jurados = countJuradosMesa(conn, id);
            if (jurados > 0 && !force) {
                ctx.status(409).json(ApiResponse.error("La mesa tiene " + jurados + " jurados asignados"));
                return;
            }
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Jurados WHERE " + juradoMesaColumn(conn) + " = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Mesa_jurados WHERE ID_MESA = ?")) {
                    ps.setLong(1, id);
                    if (ps.executeUpdate() == 0) {
                        throw new IllegalArgumentException("Mesa no encontrada");
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            ctx.json(ApiResponse.success("Mesa eliminada"));
        } catch (IllegalArgumentException e) {
            ctx.status(404).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void asignar(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            String identificacion = requiredText(body, "identificacion");
            Long idMesa = requiredLong(body, "idMesa");
            String cargo = requiredText(body, "cargo").toUpperCase();
            if (juradoExistente(conn, identificacion)) {
                ctx.status(409).json(ApiResponse.error("El votante ya está asignado como jurado"));
                return;
            }
            insertarJurado(conn, idMesa, identificacion, cargo, LocalDate.now());
            ctx.status(201).json(ApiResponse.success("Jurado asignado"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            if (handleOracle(ctx, e)) {
                return;
            }
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void asignarAleatorio(Context ctx) {
        boolean dryRun = "true".equalsIgnoreCase(ctx.queryParam("dry_run"));
        try (Connection conn = AppConfig.getConnection()) {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            Map<?, ?> configuracion = map(body.get("configuracion"));
            Long idEleccion = optionalLong(body.get("idEleccion"));
            List<Map<String, Object>> resultado = calcularAsignacion(conn, configuracion, idEleccion);
            if (!dryRun) {
                persistirAsignacion(conn, resultado, idEleccion);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("dryRun", dryRun);
            response.put("jurados", resultado);
            response.put("total", resultado.size());
            ctx.json(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void remover(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            String identificacion = ctx.pathParam("identificacion");
            Long idMesa = Long.parseLong(ctx.pathParam("idMesa"));
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Jurados WHERE " + juradoIdentificacionColumn(conn) + " = ? AND " + juradoMesaColumn(conn) + " = ?")) {
                ps.setString(1, identificacion);
                ps.setLong(2, idMesa);
                if (ps.executeUpdate() == 0) {
                    ctx.status(404).json(ApiResponse.error("Asignación no encontrada"));
                    return;
                }
            }
            ctx.json(ApiResponse.success("Jurado removido"));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void exportarPdf(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            Long idPuesto = optionalLong(ctx.queryParam("puesto"));
            Long idMesa = optionalLong(ctx.queryParam("mesa"));
            byte[] pdf = planillaPdf(conn, idPuesto, idMesa);
            ctx.header("Content-Disposition", "attachment; filename=\"planilla-jurados.pdf\"");
            ctx.contentType("application/pdf");
            ctx.result(pdf);
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    private static List<Map<String, Object>> mesasCompletas(Connection conn, Long idMesa) throws SQLException {
        String puestoId = puestoIdColumn(conn);
        String mesaPuesto = mesaPuestoColumn(conn);
        String sql = "SELECT m.ID_MESA, m.HORA_INGRESO, m.HORA_SALIDA, m.CARGO, m." + mesaPuesto + " AS ID_PUESTO, " +
                "p.NOMBRE_PUESTO, p.CIUDAD, p.SEDE FROM Mesa_jurados m " +
                "JOIN Puestos_votacion p ON p." + puestoId + " = m." + mesaPuesto + " " +
                (idMesa != null ? "WHERE m.ID_MESA = ? " : "") +
                "ORDER BY p." + puestoId + ", m.ID_MESA";
        List<Map<String, Object>> mesas = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (idMesa != null) ps.setLong(1, idMesa);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> mesa = new LinkedHashMap<>();
                    long id = rs.getLong("ID_MESA");
                    mesa.put("idMesa", id);
                    mesa.put("horaIngreso", ts(rs, "HORA_INGRESO"));
                    mesa.put("horaSalida", ts(rs, "HORA_SALIDA"));
                    mesa.put("cargo", rs.getString("CARGO"));
                    mesa.put("idPuesto", rs.getLong("ID_PUESTO"));
                    mesa.put("nombrePuesto", rs.getString("NOMBRE_PUESTO"));
                    mesa.put("ciudad", rs.getString("CIUDAD"));
                    mesa.put("sede", rs.getString("SEDE"));
                    mesa.put("jurados", juradosCompletos(conn, id));
                    mesas.add(mesa);
                }
            }
        }
        return mesas;
    }

    private static List<Map<String, Object>> juradosCompletos(Connection conn, Long idMesa) throws SQLException {
        String puestoId = puestoIdColumn(conn);
        String mesaPuesto = mesaPuestoColumn(conn);
        String juradoMesa = juradoMesaColumn(conn);
        String juradoIdent = juradoIdentificacionColumn(conn);
        String sql = "SELECT j." + juradoMesa + " AS ID_MESA, j." + juradoIdent + " AS IDENTIFICACION, j.FECHA_ASIGNACION, j.CARGO, " +
                "v.CORREO, v.PRIMER_NOMBRE, v.SEGUNDO_NOMBRE, v.PRIMER_APELLIDO, v.SEGUNDO_APELLIDO, " +
                "v.ESTADO_VOTO, v.ID_ROL, v.ID_PUESTO AS PUESTO_HABITUAL_ID, " +
                "ph.NOMBRE_PUESTO AS PUESTO_HABITUAL, ph.CIUDAD AS CIUDAD_HABITUAL, ph.SEDE AS SEDE_HABITUAL, " +
                "m." + mesaPuesto + " AS PUESTO_ASIGNADO_ID, pa.NOMBRE_PUESTO AS PUESTO_ASIGNADO, pa.CIUDAD AS CIUDAD_ASIGNADA, pa.SEDE AS SEDE_ASIGNADA " +
                "FROM Jurados j " +
                "JOIN Votantes v ON v.IDENTIFICACION = j." + juradoIdent + " " +
                "JOIN Mesa_jurados m ON m.ID_MESA = j." + juradoMesa + " " +
                "LEFT JOIN Puestos_votacion ph ON ph." + puestoId + " = v.ID_PUESTO " +
                "LEFT JOIN Puestos_votacion pa ON pa." + puestoId + " = m." + mesaPuesto + " " +
                (idMesa != null ? "WHERE j." + juradoMesa + " = ? " : "") +
                "ORDER BY j." + juradoMesa + ", j.CARGO, v.PRIMER_APELLIDO";
        List<Map<String, Object>> jurados = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (idMesa != null) ps.setLong(1, idMesa);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    jurados.add(mapJurado(rs));
                }
            }
        }
        return jurados;
    }

    private static Map<String, Object> mapJurado(ResultSet rs) throws SQLException {
        Map<String, Object> jurado = new LinkedHashMap<>();
        jurado.put("idMesa", rs.getLong("ID_MESA"));
        jurado.put("identificacion", rs.getString("IDENTIFICACION"));
        jurado.put("fechaAsignacion", rs.getDate("FECHA_ASIGNACION") != null ? rs.getDate("FECHA_ASIGNACION").toString() : null);
        jurado.put("cargo", rs.getString("CARGO"));
        jurado.put("correo", rs.getString("CORREO"));
        jurado.put("nombreCompleto", fullName(rs));
        jurado.put("rol", ROLES.getOrDefault(rs.getLong("ID_ROL"), "ROL " + rs.getLong("ID_ROL")));
        jurado.put("estadoVoto", rs.getString("ESTADO_VOTO"));
        jurado.put("idPuestoHabitual", rs.getLong("PUESTO_HABITUAL_ID"));
        jurado.put("puestoHabitual", rs.getString("PUESTO_HABITUAL"));
        jurado.put("idPuestoAsignado", rs.getLong("PUESTO_ASIGNADO_ID"));
        jurado.put("puestoAsignado", rs.getString("PUESTO_ASIGNADO"));
        return jurado;
    }

    private static String fullName(ResultSet rs) throws SQLException {
        return String.join(" ",
                nonNull(rs.getString("PRIMER_NOMBRE")),
                nonNull(rs.getString("SEGUNDO_NOMBRE")),
                nonNull(rs.getString("PRIMER_APELLIDO")),
                nonNull(rs.getString("SEGUNDO_APELLIDO"))).replaceAll("\\s+", " ").trim();
    }

    private static List<Map<String, Object>> calcularAsignacion(Connection conn, Map<?, ?> config, Long idEleccion) throws SQLException {
        List<Map<String, Object>> pool = poolElegibles(conn, map(config.get("poolConfig")), idEleccion);
        List<Map<String, Object>> puestos = puestos(conn);
        if (pool.isEmpty() || puestos.isEmpty()) return List.of();

        Collections.shuffle(pool, new Random());
        List<Map<String, Object>> slots = construirSlots(config, puestos);
        List<Map<String, Object>> resultado = new ArrayList<>();
        Set<String> usados = new HashSet<>();
        String restriccion = text(map(config.get("distribucionConfig")), "puestoAsignado");
        for (Map<String, Object> slot : slots) {
            Map<String, Object> puesto = mapString(slot.get("puesto"));
            Map<String, Object> elegido = elegirCercano(pool, usados, puesto, restriccion);
            if (elegido == null) break;
            usados.add((String) elegido.get("identificacion"));
            Map<String, Object> asignacion = new LinkedHashMap<>();
            asignacion.putAll(elegido);
            asignacion.put("idMesa", slot.get("idMesa"));
            asignacion.put("idPuestoAsignado", puesto.get("idPuesto"));
            asignacion.put("nombrePuesto", puesto.get("nombrePuesto"));
            asignacion.put("cargo", slot.get("cargo"));
            asignacion.put("horaIngreso", slot.get("horaIngreso"));
            asignacion.put("horaSalida", slot.get("horaSalida"));
            asignacion.put("fechaAsignacion", LocalDate.now().toString());
            resultado.add(asignacion);
        }
        return resultado;
    }

    private static Map<String, Object> elegirCercano(List<Map<String, Object>> pool, Set<String> usados, Map<String, Object> puesto, String restriccion) {
        return pool.stream()
                .filter(v -> !usados.contains(v.get("identificacion")))
                .min(Comparator.comparingInt(v -> score(v, puesto, restriccion)))
                .orElse(null);
    }

    private static int score(Map<String, Object> votante, Map<String, Object> puesto, String restriccion) {
        int score = 0;
        if (!eq(votante.get("sede"), puesto.get("sede"))) score += 20;
        if (!eq(votante.get("ciudad"), puesto.get("ciudad"))) score += 10;
        boolean mismo = eq(votante.get("idPuesto"), puesto.get("idPuesto"));
        if ("mismo".equals(restriccion)) {
            if (!mismo) score += 15;
        } else if ("distinto".equals(restriccion) || restriccion == null || restriccion.isBlank()) {
            if (mismo) score += 50;
        }
        return score;
    }

    private static List<Map<String, Object>> construirSlots(Map<?, ?> config, List<Map<String, Object>> puestos) {
        Map<?, ?> dist = map(config.get("distribucionConfig"));
        List<Map<String, Object>> turnos = listOfMaps(config.get("turnos"));
        if (turnos.isEmpty()) turnos = List.of(Map.of("inicio", "08:00", "fin", "12:00"));
        String modo = text(dist, "modo");
        if (modo == null || modo.isBlank()) modo = "fijo_por_puesto";
        List<Map<String, Object>> slots = new ArrayList<>();
        if ("por_cargo".equals(modo)) {
            Map<?, ?> cargos = map(dist.get("cargos"));
            for (Map<String, Object> puesto : puestos) {
                for (Map<String, Object> turno : turnos) {
                    cargos.forEach((cargo, value) -> {
                        for (int i = 0; i < intValue(value, 0); i++) slots.add(slot(puesto, String.valueOf(cargo), turno));
                    });
                }
            }
            return slots;
        }
        int total;
        if ("porcentaje".equals(modo)) {
            total = Math.max(1, intValue(dist.get("totalEstimado"), intValue(dist.get("porcentaje"), 5)));
        } else if ("total_manual".equals(modo)) {
            total = Math.max(1, intValue(dist.get("totalManual"), puestos.size()));
        } else {
            total = Math.max(1, intValue(dist.get("valorFijo"), 3)) * puestos.size();
        }
        String[] cargos = {"PRESIDENTE", "VOCAL", "SECRETARIO"};
        for (int i = 0; i < total; i++) {
            Map<String, Object> puesto = puestos.get(i % puestos.size());
            Map<String, Object> turno = turnos.get((i / puestos.size()) % turnos.size());
            slots.add(slot(puesto, cargos[i % cargos.length], turno));
        }
        return slots;
    }

    private static int calcularTotalRequerido(Map<?, ?> dist, int poolSize, int mesas) {
        String modo = text(dist, "modo");
        if (modo == null || modo.isBlank()) modo = "fijo_por_puesto";
        if ("porcentaje".equals(modo)) {
            int porcentaje = intValue(dist.get("porcentaje"), 5);
            return poolSize > 0 ? Math.max(1, (int) Math.ceil(poolSize * (porcentaje / 100.0))) : 0;
        }
        if ("total_manual".equals(modo)) {
            return Math.max(0, intValue(dist.get("totalManual"), 0));
        }
        if ("por_cargo".equals(modo)) {
            Map<?, ?> cargos = map(dist.get("cargos"));
            int porMesa = 0;
            for (Object value : cargos.values()) {
                porMesa += intValue(value, 0);
            }
            return Math.max(0, porMesa * mesas);
        }
        return Math.max(0, intValue(dist.get("valorFijo"), 3) * mesas);
    }

    private static Map<String, Object> slot(Map<String, Object> puesto, String cargo, Map<String, Object> turno) {
        Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("idMesa", null);
        slot.put("puesto", puesto);
        slot.put("cargo", cargo);
        slot.put("horaIngreso", text(turno, "inicio"));
        slot.put("horaSalida", text(turno, "fin"));
        return slot;
    }

    private static void persistirAsignacion(Connection conn, List<Map<String, Object>> resultado, Long idEleccion) throws SQLException {
        conn.setAutoCommit(false);
        try {
            Map<String, Long> mesas = new HashMap<>();
            for (Map<String, Object> asignacion : resultado) {
                String key = asignacion.get("idPuestoAsignado") + "|" + asignacion.get("horaIngreso") + "|" + asignacion.get("horaSalida");
                Long idMesa = mesas.get(key);
                if (idMesa == null) {
                    Map<String, Object> mesa = new HashMap<>();
                    mesa.put("idPuesto", optionalLong(asignacion.get("idPuestoAsignado")));
                    mesa.put("cargo", "JURADOS");
                    mesa.put("horaIngreso", Timestamp.valueOf(LocalDateTime.of(LocalDate.now(), LocalTime.parse((String) asignacion.get("horaIngreso")))));
                    mesa.put("horaSalida", Timestamp.valueOf(LocalDateTime.of(LocalDate.now(), LocalTime.parse((String) asignacion.get("horaSalida")))));
                    mesa.put("idEleccion", idEleccion);
                    idMesa = insertarMesa(conn, mesa);
                    mesas.put(key, idMesa);
                }
                asignacion.put("idMesa", idMesa);
                insertarJurado(conn, idMesa, (String) asignacion.get("identificacion"), (String) asignacion.get("cargo"), LocalDate.now());
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static List<Map<String, Object>> poolElegibles(Connection conn, Map<?, ?> poolConfig, Long idEleccion) throws SQLException {
        String puestoId = puestoIdColumn(conn);
        String juradoIdent = juradoIdentificacionColumn(conn);
        List<String> roles = listOfStrings(poolConfig.get("roles"));
        List<String> estados = listOfStrings(poolConfig.get("estados"));
        boolean biometrico = Boolean.TRUE.equals(poolConfig.get("requerirBiometrico"));
        boolean excluirCandidatos = !Boolean.FALSE.equals(poolConfig.get("excluirCandidatos"));
        StringBuilder sql = new StringBuilder("SELECT v.IDENTIFICACION, v.CORREO, v.PRIMER_NOMBRE, v.SEGUNDO_NOMBRE, v.PRIMER_APELLIDO, v.SEGUNDO_APELLIDO, " +
                "v.ESTADO_VOTO, v.ID_ROL, v.ID_PUESTO, p.NOMBRE_PUESTO, p.CIUDAD, p.SEDE, " +
                "CASE WHEN bv.ID_BIOMETRIA IS NULL THEN 0 ELSE 1 END AS TIENE_BIOMETRICO " +
                "FROM Votantes v JOIN Puestos_votacion p ON p." + puestoId + " = v.ID_PUESTO " +
                "LEFT JOIN Biometria_votantes bv ON bv.IDENTIFICACION = v.IDENTIFICACION AND bv.ACTIVO = 'S' ");
        List<Object> params = new ArrayList<>();
        if (idEleccion != null && idEleccion > 0) {
            sql.append("INNER JOIN Eleccion_roles er ON er.id_rol = v.id_rol AND er.id_eleccion = ? ");
            params.add(idEleccion);
        }
        sql.append("WHERE 1=1 AND v.FECHA_NACIMIENTO IS NOT NULL ");
        if (!roles.isEmpty()) {
            sql.append("AND v.ID_ROL IN (").append(placeholders(roles.size())).append(") ");
            roles.forEach(role -> params.add(roleId(role)));
        }
        if (!estados.isEmpty()) {
            sql.append("AND v.ESTADO_VOTO IN (").append(placeholders(estados.size())).append(") ");
            params.addAll(estados);
        }
        if (biometrico) {
            sql.append("AND bv.ID_BIOMETRIA IS NOT NULL ");
        }
        sql.append("AND NOT EXISTS (SELECT 1 FROM Jurados j WHERE j.").append(juradoIdent).append(" = v.IDENTIFICACION) ");
        // El esquema actual no relaciona Candidatos con Votantes. La bandera se conserva
        // en la API para conectar esa exclusión cuando exista esa relación de datos.
        List<Map<String, Object>> pool = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("identificacion", rs.getString("IDENTIFICACION"));
                    item.put("correo", rs.getString("CORREO"));
                    item.put("nombreCompleto", fullName(rs));
                    item.put("rol", ROLES.getOrDefault(rs.getLong("ID_ROL"), "ROL " + rs.getLong("ID_ROL")));
                    item.put("estadoVoto", rs.getString("ESTADO_VOTO"));
                    item.put("idPuesto", rs.getLong("ID_PUESTO"));
                    item.put("puestoHabitual", rs.getString("NOMBRE_PUESTO"));
                    item.put("ciudad", rs.getString("CIUDAD"));
                    item.put("sede", rs.getString("SEDE"));
                    item.put("tieneBiometrico", rs.getInt("TIENE_BIOMETRICO") == 1);
                    pool.add(item);
                }
            }
        }
        return pool;
    }

    private static List<Map<String, Object>> puestos(Connection conn) throws SQLException {
        String puestoId = puestoIdColumn(conn);
        List<Map<String, Object>> puestos = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT " + puestoId + " AS ID_PUESTO, NOMBRE_PUESTO, CIUDAD, SEDE FROM Puestos_votacion ORDER BY " + puestoId);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> puesto = new LinkedHashMap<>();
                puesto.put("idPuesto", rs.getLong("ID_PUESTO"));
                puesto.put("nombrePuesto", rs.getString("NOMBRE_PUESTO"));
                puesto.put("ciudad", rs.getString("CIUDAD"));
                puesto.put("sede", rs.getString("SEDE"));
                puestos.add(puesto);
            }
        }
        return puestos;
    }

    private static byte[] planillaPdf(Connection conn, Long idPuesto, Long idMesa) throws SQLException {
        List<String> lines = new ArrayList<>();
        lines.add("Planilla oficial de jurados");
        lines.add("Mesa | Jurado | Identificacion | Cargo | Puesto | Firma");
        for (Map<String, Object> mesa : mesasCompletas(conn, idMesa)) {
            if (idPuesto != null && !idPuesto.equals(optionalLong(mesa.get("idPuesto")))) continue;
            for (Object obj : (List<?>) mesa.get("jurados")) {
                Map<?, ?> jurado = (Map<?, ?>) obj;
                lines.add("Mesa " + mesa.get("idMesa") + " | " + jurado.get("nombreCompleto") + " | " +
                        jurado.get("identificacion") + " | " + jurado.get("cargo") + " | " +
                        mesa.get("nombrePuesto") + " | ____________________");
            }
        }
        if (lines.size() == 2) {
            lines.add("Sin jurados asignados para los filtros seleccionados.");
        }
        return simplePdf(lines);
    }

    private static byte[] simplePdf(List<String> lines) {
        StringBuilder content = new StringBuilder("BT /F1 11 Tf 50 790 Td 14 TL ");
        for (String line : lines) {
            content.append("(").append(pdfEscape(line)).append(") Tj T* ");
        }
        content.append("ET");
        byte[] stream = content.toString().getBytes(StandardCharsets.ISO_8859_1);
        List<String> objects = new ArrayList<>();
        objects.add("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n");
        objects.add("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n");
        objects.add("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n");
        objects.add("4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n");
        objects.add("5 0 obj << /Length " + stream.length + " >> stream\n" + content + "\nendstream endobj\n");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (String object : objects) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            pdf.append(object);
        }
        int xref = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        pdf.append("xref\n0 6\n0000000000 65535 f \n");
        for (Integer offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer << /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String pdfEscape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
                .replace("Á", "A").replace("É", "E").replace("Í", "I").replace("Ó", "O").replace("Ú", "U")
                .replace("ñ", "n").replace("Ñ", "N");
    }

    private static Long insertarMesa(Connection conn, Map<String, Object> mesa) throws SQLException {
        long id;
        try (PreparedStatement seq = conn.prepareStatement("SELECT seq_mesa_jurados.NEXTVAL FROM dual");
             ResultSet rs = seq.executeQuery()) {
            rs.next();
            id = rs.getLong(1);
        }
        String sql = "INSERT INTO Mesa_jurados (ID_MESA, HORA_INGRESO, HORA_SALIDA, CARGO, " + mesaPuestoColumn(conn) + ", ID_ELECCION) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setTimestamp(2, (Timestamp) mesa.get("horaIngreso"));
            ps.setTimestamp(3, (Timestamp) mesa.get("horaSalida"));
            ps.setString(4, (String) mesa.get("cargo"));
            ps.setLong(5, (Long) mesa.get("idPuesto"));
            setLongOrNull(ps, 6, (Number) mesa.get("idEleccion"));
            ps.executeUpdate();
        }
        return id;
    }

    private static void insertarJurado(Connection conn, Long idMesa, String identificacion, String cargo, LocalDate fecha) throws SQLException {
        String sql = "INSERT INTO Jurados (" + juradoMesaColumn(conn) + ", " + juradoIdentificacionColumn(conn) + ", FECHA_ASIGNACION, CARGO) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idMesa);
            ps.setString(2, identificacion);
            ps.setDate(3, Date.valueOf(fecha != null ? fecha : LocalDate.now()));
            ps.setString(4, cargo);
            ps.executeUpdate();
        }
    }

    private static boolean juradoExistente(Connection conn, String identificacion) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM Jurados WHERE " + juradoIdentificacionColumn(conn) + " = ?")) {
            ps.setString(1, identificacion);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private static int countJuradosMesa(Connection conn, Long idMesa) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM Jurados WHERE " + juradoMesaColumn(conn) + " = ?")) {
            ps.setLong(1, idMesa);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static Map<String, Object> parseMesa(Map<?, ?> body) {
        Long idPuesto = requiredLong(body, "idPuesto");
        String cargo = requiredText(body, "cargo").toUpperCase();
        Timestamp ingreso = parseTimestamp(requiredText(body, "horaIngreso"));
        Timestamp salida = parseTimestamp(requiredText(body, "horaSalida"));
        if (!salida.after(ingreso)) {
            throw new IllegalArgumentException("La hora de salida debe ser posterior a la hora de ingreso");
        }
        Map<String, Object> mesa = new HashMap<>();
        mesa.put("idPuesto", idPuesto);
        mesa.put("cargo", cargo);
        mesa.put("horaIngreso", ingreso);
        mesa.put("horaSalida", salida);
        mesa.put("idEleccion", body.getOrDefault("idEleccion", null));
        return mesa;
    }

    private static Timestamp parseTimestamp(String value) {
        String normalized = value.contains("T") ? value : LocalDate.now() + "T" + value;
        if (normalized.length() == 16) normalized += ":00";
        return Timestamp.valueOf(LocalDateTime.parse(normalized));
    }

    private static LocalDate parseLocalDate(String value) {
        return value == null || value.isBlank() ? LocalDate.now() : LocalDate.parse(value);
    }

    private static void setLongOrNull(PreparedStatement ps, int idx, Number value) throws SQLException {
        if (value != null) {
            ps.setLong(idx, value.longValue());
        } else {
            ps.setNull(idx, java.sql.Types.BIGINT);
        }
    }

    private static String ts(ResultSet rs, String field) throws SQLException {
        Timestamp ts = rs.getTimestamp(field);
        return ts != null ? ts.toLocalDateTime().toString() : null;
    }

    private static String puestoIdColumn(Connection conn) throws SQLException {
        return firstColumn(conn, "PUESTOS_VOTACION", "ID_PUESTO", "ID_PUESTOS", "IDPUESTOS");
    }

    private static String mesaPuestoColumn(Connection conn) throws SQLException {
        return firstColumn(conn, "MESA_JURADOS", "ID_PUESTO", "ID_PUESTOS", "PUESTOS_VOTACION_IDPUESTOS");
    }

    private static String juradoMesaColumn(Connection conn) throws SQLException {
        return firstColumn(conn, "JURADOS", "ID_MESA", "MESA_JURADOS_IDMESA");
    }

    private static String juradoIdentificacionColumn(Connection conn) throws SQLException {
        return firstColumn(conn, "JURADOS", "IDENTIFICACION", "VOTANTES_IDENTIFICACION");
    }

    private static String firstColumn(Connection conn, String table, String... candidates) throws SQLException {
        for (String candidate : candidates) {
            if (columnExists(conn, table, candidate)) {
                return candidate;
            }
        }
        throw new SQLException("No se encontró columna compatible en " + table);
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, conn.getSchema(), table.toUpperCase(), column.toUpperCase())) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = meta.getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            return rs.next();
        }
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapString(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static List<String> listOfStrings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String requiredText(Map<?, ?> map, String key) {
        String value = text(map, key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " requerido");
        return value;
    }

    private static Long requiredLong(Map<?, ?> map, String key) {
        Long value = optionalLong(map.get(key));
        if (value == null) throw new IllegalArgumentException(key + " requerido");
        return value;
    }

    private static Long optionalLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return Long.parseLong(String.valueOf(value));
    }

    private static int intValue(Object value, int fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        return (int) Math.round(Double.parseDouble(String.valueOf(value)));
    }

    private static long roleId(String role) {
        String normalized = role.toUpperCase();
        return ROLES.entrySet().stream()
                .filter(e -> e.getValue().equals(normalized))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(1L);
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static boolean eq(Object a, Object b) {
        return a != null && b != null && String.valueOf(a).equalsIgnoreCase(String.valueOf(b));
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    private static boolean handleOracle(Context ctx, Throwable e) {
        return OracleErrorHandler.from(e).map(error -> {
            ctx.status(error.statusCode()).json(ApiResponse.error(error.message()));
            return true;
        }).orElse(false);
    }
}
