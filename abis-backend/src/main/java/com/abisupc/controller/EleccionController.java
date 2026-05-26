package com.abisupc.controller;

import com.abisupc.config.AppConfig;
import com.abisupc.dto.ApiResponse;
import com.abisupc.model.Eleccion;
import com.abisupc.model.EstadoEleccion;
import com.abisupc.repository.EleccionRepository;
import com.abisupc.repository.EleccionRolRepository;
import com.abisupc.service.AdminService;
import com.abisupc.service.EleccionLifecycleService;
import com.abisupc.service.ResultadosService;
import com.abisupc.util.OracleErrorHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EleccionController {

    private static final EleccionRepository eleccionRepo = new EleccionRepository();
    private static final EleccionRolRepository eleccionRolRepo = new EleccionRolRepository();
    private static final AdminService adminService = new AdminService();
    private static final EleccionLifecycleService lifecycleService = new EleccionLifecycleService();
    private static final ResultadosService resultadosService = new ResultadosService();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void getAll(Context ctx) {
        try {
            lifecycleService.sincronizarEstados();
            String estado = ctx.queryParam("estado");
            List<Eleccion> fuente = estado != null && !estado.isBlank()
                    ? eleccionRepo.findByEstado(estado.trim().toUpperCase())
                    : eleccionRepo.findAll();
            List<Map<String, Object>> elecciones = new ArrayList<>();
            try (Connection conn = AppConfig.getConnection()) {
                for (Eleccion eleccion : fuente) {
                    elecciones.add(toResponse(eleccion, conn));
                }
            }
            ctx.json(ApiResponse.success(elecciones));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void crear(Context ctx) {
        try {
            JsonNode body = mapper.readTree(ctx.body());
            Eleccion eleccion = parseEleccion(body);
            eleccionRepo.save(eleccion);
            configurarPesosRolesSiExisten(eleccion.getId(), body);
            ctx.status(201).json(ApiResponse.success(toResponse(eleccion)));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void stats(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            lifecycleService.sincronizarEstados();
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("total", count(conn, "SELECT COUNT(*) FROM Elecciones"));
            stats.put("programadas", count(conn, "SELECT COUNT(*) FROM Elecciones WHERE ESTADO = 'PROGRAMADA'"));
            stats.put("enCurso", count(conn, "SELECT COUNT(*) FROM Elecciones WHERE ESTADO = 'EN_CURSO'"));
            stats.put("finalizadas", count(conn, "SELECT COUNT(*) FROM Elecciones WHERE ESTADO IN ('CERRADA', 'FINALIZADA')"));
            stats.put("canceladas", count(conn, "SELECT COUNT(*) FROM Elecciones WHERE ESTADO = 'CANCELADA'"));
            stats.put("electoresHabilitados", count(conn,
                    "SELECT COUNT(*) FROM Votantes WHERE UPPER(ESTADO_VOTO) IN ('PENDIENTE', 'EJERCIDO')"));
            stats.put("proximas", count(conn, "SELECT COUNT(*) FROM Elecciones WHERE ESTADO = 'PROGRAMADA'"));
            ctx.json(stats);
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void preparacion(Context ctx) {
        try (Connection conn = AppConfig.getConnection()) {
            Long id = Long.parseLong(ctx.pathParam("id"));
            int candidatos = (int) countById(conn, "SELECT COUNT(*) FROM Candidatos_eleccion WHERE ID_ELECCION = ?", id);
            int roles = (int) countById(conn, "SELECT COUNT(*) FROM Eleccion_roles WHERE ID_ELECCION = ?", id);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("definirCandidatos", candidatos > 0);
            data.put("asignarJurados", true);
            data.put("configurarPuestos", true);
            data.put("publicacionOficial", false);
            data.put("porcentaje", ((candidatos > 0 ? 1 : 0) + 1 + 1 + 0) * 25);
            data.put("rolesConfigurados", roles);
            ctx.json(data);
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void editar(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            JsonNode body = mapper.readTree(ctx.body());
            Eleccion eleccion = parseEleccion(body);
            eleccion.setId(id);
            eleccionRepo.update(eleccion);
            configurarPesosRolesSiExisten(id, body);
            ctx.json(ApiResponse.success("Elección actualizada"));
        } catch (IllegalStateException e) {
            ctx.status(409).json(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void iniciar(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            lifecycleService.sincronizarEstados();

            Eleccion eleccion = eleccionRepo.findById(id).orElse(null);
            if (eleccion != null && eleccion.getEstado() == EstadoEleccion.EN_CURSO) {
                ctx.json(ApiResponse.success("Elección ya estaba en curso"));
                return;
            }
            if (eleccion == null || eleccion.getEstado() != EstadoEleccion.PROGRAMADA) {
                ctx.status(409).json(ApiResponse.error("La elección debe estar en estado PROGRAMADA"));
                return;
            }
            var activa = lifecycleService.eleccionEnCurso();
            if (activa.isPresent()) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("motivo", "YA_EXISTE_ELECCION_EN_CURSO");
                detail.put("activa", activa.get());
                detail.put("solicitada", Map.of("id", id, "nombre", eleccion.getNombre()));
                detail.put("mensaje", "Primero cierre la elección activa para iniciar esta jornada");
                ctx.status(409).json(ApiResponse.error(mapper.writeValueAsString(detail)));
                return;
            }

            eleccionRepo.cambiarEstado(id, "EN_CURSO");
            ctx.json(ApiResponse.success("Elección iniciada"));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void cerrar(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Eleccion eleccion = eleccionRepo.findById(id).orElse(null);
            if (eleccion == null || eleccion.getEstado() != EstadoEleccion.EN_CURSO) {
                ctx.status(409).json(ApiResponse.error("La elección debe estar en estado EN CURSO"));
                return;
            }

            adminService.cerrarEleccion(id, ctx.attribute("idAdmin"));
            ctx.json(ApiResponse.success("Elección cerrada"));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void cerrarAdmin(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Long idAdmin = ctx.attribute("idAdmin");
            adminService.cerrarEleccion(id, idAdmin);
            ctx.json(ApiResponse.success("Eleccion cerrada"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            if (handleOracle(ctx, e)) {
                return;
            }
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void resultados(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(ApiResponse.success(resultadosService.resultadosEleccion(id)));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            if (handleOracle(ctx, e)) {
                return;
            }
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void resultadosDirectos(Context ctx) {
        try {
            Long idEleccion = Long.parseLong(ctx.pathParam("id"));
            List<Map<String, Object>> candidatos = new ArrayList<>();
            try (Connection conn = AppConfig.getConnection()) {
                String pesoCol = pesoColumn(conn);
                String sql = "SELECT c.PRIMER_NOMBRE || ' ' || c.PRIMER_APELLIDO AS candidato, " +
                        "ce.CARGO, NVL(SUM(v." + pesoCol + "), 0) AS votos " +
                        "FROM Candidatos_eleccion ce " +
                        "JOIN Candidatos c ON c.ID_CANDIDATO = ce.ID_CANDIDATO " +
                        "LEFT JOIN Votos v ON v.ID_CANDIDATO = ce.ID_CANDIDATO AND v.ID_ELECCION = ce.ID_ELECCION " +
                        "WHERE ce.ID_ELECCION = ? " +
                        "GROUP BY c.PRIMER_NOMBRE, c.PRIMER_APELLIDO, ce.CARGO " +
                        "ORDER BY votos DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, idEleccion);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> c = new LinkedHashMap<>();
                            c.put("candidato", rs.getString("candidato"));
                            c.put("cargo", rs.getString("CARGO"));
                            c.put("votos", rs.getDouble("votos"));
                            candidatos.add(c);
                        }
                    }
                }
            }
            ctx.json(ApiResponse.success(Map.of("candidatos", candidatos)));
        } catch (NumberFormatException e) {
            ctx.status(400).json(ApiResponse.error("ID de eleccion invalido"));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void eliminar(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            if (eleccionRepo.tieneVotos(id)) {
                ctx.status(409).json(ApiResponse.error("No se puede eliminar: tiene votos asociados"));
                return;
            }

            eleccionRepo.delete(id);
            ctx.json(ApiResponse.success("Elección eliminada"));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void getRoles(Context ctx) {
        try {
            lifecycleService.sincronizarEstados();
            Long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(ApiResponse.success(eleccionRolRepo.findByEleccion(id)));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void configurarRol(Context ctx) {
        try {
            Long idEleccion = Long.parseLong(ctx.pathParam("id"));
            JsonNode body = mapper.readTree(ctx.body());
            Long idRol = body.hasNonNull("idRol") ? body.get("idRol").asLong() : null;
            Double pesoVoto = body.hasNonNull("pesoVoto") ? body.get("pesoVoto").asDouble() : null;
            if (idRol == null) {
                throw new IllegalArgumentException("idRol requerido");
            }
            if (pesoVoto == null || pesoVoto <= 0) {
                throw new IllegalArgumentException("pesoVoto debe ser mayor que 0");
            }
            validarProgramada(idEleccion);

            eleccionRolRepo.save(idEleccion, idRol, pesoVoto);
            ctx.status(201).json(ApiResponse.success("Peso de voto configurado"));
        } catch (IllegalStateException e) {
            ctx.status(409).json(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void elegibilidad(Context ctx) {
        try {
            Long idEleccion = Long.parseLong(ctx.pathParam("id"));
            String sql = """
                    SELECT r.id_rol, r.nombre, er.peso_voto,
                           COUNT(v.identificacion) AS total,
                           COUNT(CASE WHEN UPPER(v.estado_voto) = 'PENDIENTE' THEN 1 END) AS pendientes,
                           NVL(ej.ejercido, 0) AS ejercido
                    FROM Eleccion_roles er
                    JOIN Roles r ON er.id_rol = r.id_rol
                    LEFT JOIN Votantes v ON v.id_rol = er.id_rol
                    LEFT JOIN (
                        SELECT vt.id_rol, COUNT(*) AS ejercido
                        FROM Registro_votos rv
                        JOIN Votantes vt ON rv.identificacion = vt.identificacion
                        WHERE rv.id_eleccion = ?
                        GROUP BY vt.id_rol
                    ) ej ON ej.id_rol = er.id_rol
                    WHERE er.id_eleccion = ?
                    GROUP BY r.id_rol, r.nombre, er.peso_voto, ej.ejercido
                    ORDER BY r.id_rol
                    """;
            List<Map<String, Object>> roles = new ArrayList<>();
            long totalPendientes = 0;
            long totalEjercido = 0;
            try (Connection conn = AppConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, idEleccion);
                ps.setLong(2, idEleccion);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> rol = new LinkedHashMap<>();
                        rol.put("idRol", rs.getLong("id_rol"));
                        rol.put("nombre", rs.getString("nombre"));
                        rol.put("pesoVoto", rs.getDouble("peso_voto"));
                        rol.put("total", rs.getLong("total"));
                        rol.put("pendientes", rs.getLong("pendientes"));
                        rol.put("ejercido", rs.getLong("ejercido"));
                        roles.add(rol);
                        totalPendientes += rs.getLong("pendientes");
                        totalEjercido += rs.getLong("ejercido");
                    }
                }
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("idEleccion", idEleccion);
            response.put("roles", roles);
            response.put("totalElegibles", roles.stream().mapToLong(r -> (Long) r.get("total")).sum());
            response.put("totalPendientes", totalPendientes);
            response.put("totalEjercido", totalEjercido);
            ctx.json(ApiResponse.success(response));
        } catch (NumberFormatException e) {
            ctx.status(400).json(ApiResponse.error("ID de eleccion invalido"));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    private static Eleccion parseEleccion(JsonNode body) {
        String nombre = text(body, "nombre");
        String fechaHoraInicio = text(body, "fechaHoraInicio");
        String fechaHoraFin = text(body, "fechaHoraFin");

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Nombre requerido");
        }
        if (fechaHoraInicio == null || fechaHoraInicio.isBlank() || fechaHoraFin == null || fechaHoraFin.isBlank()) {
            throw new IllegalArgumentException("Fechas requeridas");
        }

        LocalDateTime inicio = LocalDateTime.parse(fechaHoraInicio);
        LocalDateTime fin = LocalDateTime.parse(fechaHoraFin);
        if (!inicio.isBefore(fin)) {
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin");
        }

        Eleccion eleccion = new Eleccion();
        eleccion.setNombre(nombre.trim());
        eleccion.setFechaHoraInicio(inicio);
        eleccion.setFechaHoraFin(fin);
        eleccion.setEstado(EstadoEleccion.PROGRAMADA);
        return eleccion;
    }

    private static String text(JsonNode body, String field) {
        return body != null && body.hasNonNull(field) ? body.get(field).asText() : null;
    }

    private static void configurarPesosRolesSiExisten(Long idEleccion, JsonNode body) {
        if (idEleccion == null || body == null || !body.has("pesosRoles")) {
            return;
        }
        JsonNode pesos = body.get("pesosRoles");
        validarProgramada(idEleccion);
        configurarPeso(idEleccion, pesos, "estudiante", "Estudiante");
        configurarPeso(idEleccion, pesos, "docente", "Docente");
        configurarPeso(idEleccion, pesos, "egresado", "Egresado");
        configurarPeso(idEleccion, pesos, "administrativo", "Administrativo");
    }

    private static void configurarPeso(Long idEleccion, JsonNode pesos, String field, String nombreRol) {
        if (pesos != null && pesos.hasNonNull(field)) {
            double peso = pesos.get(field).asDouble();
            if (peso > 0) {
                Long idRol = eleccionRolRepo.findRolIdByNombre(nombreRol);
                eleccionRolRepo.save(idEleccion, idRol, peso);
            }
        }
    }

    private static void validarProgramada(Long idEleccion) {
        Eleccion eleccion = eleccionRepo.findById(idEleccion)
                .orElseThrow(() -> new IllegalArgumentException("Eleccion no encontrada"));
        if (eleccion.getEstado() != EstadoEleccion.PROGRAMADA) {
            throw new IllegalStateException("Los pesos de voto solo se pueden cambiar antes de iniciar la eleccion");
        }
    }

    private static Map<String, Object> toResponse(Eleccion eleccion) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", eleccion.getId());
        data.put("nombre", eleccion.getNombre());
        data.put("fechaHoraInicio", eleccion.getFechaHoraInicio() != null ? eleccion.getFechaHoraInicio().toString() : null);
        data.put("fechaHoraFin", eleccion.getFechaHoraFin() != null ? eleccion.getFechaHoraFin().toString() : null);
        data.put("estado", eleccion.getEstado() != null ? eleccion.getEstado().getDbValue() : null);
        return data;
    }

    private static Map<String, Object> toResponse(Eleccion eleccion, Connection conn) throws SQLException {
        Map<String, Object> data = toResponse(eleccion);
        long id = eleccion.getId();
        long totalHabilitados = count(conn,
                "SELECT COUNT(*) FROM Votantes WHERE UPPER(ESTADO_VOTO) IN ('PENDIENTE', 'EJERCIDO')");
        long votos = countById(conn, "SELECT COUNT(*) FROM Registro_votos WHERE ID_ELECCION = ?", id);
        data.put("candidatos", countById(conn, "SELECT COUNT(*) FROM Candidatos_eleccion WHERE ID_ELECCION = ?", id));
        data.put("votosRegistrados", votos);
        data.put("participacion", totalHabilitados > 0 ? Math.round((votos * 100.0) / totalHabilitados) : 0);
        return data;
    }

    private static long count(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static long countById(Connection conn, String sql, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static boolean handleOracle(Context ctx, Throwable e) {
        return OracleErrorHandler.from(e).map(error -> {
            ctx.status(error.statusCode()).json(ApiResponse.error(error.message()));
            return true;
        }).orElse(false);
    }

    public static void actaPDF(Context ctx) {
        try {
            Long idEleccion = Long.parseLong(ctx.pathParam("id"));
            String nombre = "";
            String fecha = "";
            StringBuilder filas = new StringBuilder();
            String ganador = "";
            long totalVotos = 0;
            int nCandidatos = 0;
            try (Connection conn = AppConfig.getConnection()) {
                String sqlInfo = "SELECT NOMBRE, TO_CHAR(FECHA_HORA_INICIO,'DD/MM/YYYY') AS FECHA FROM ELECCIONES WHERE ID_ELECCION = ?";
                try (PreparedStatement ps = conn.prepareStatement(sqlInfo)) {
                    ps.setLong(1, idEleccion);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) { nombre = rs.getString("NOMBRE"); fecha = rs.getString("FECHA"); }
                    }
                }

                String pesoCol = pesoColumn(conn);
                String sqlResultados = "SELECT c.PRIMER_NOMBRE || ' ' || c.PRIMER_APELLIDO AS candidato, " +
                        "ce.CARGO, NVL(SUM(v." + pesoCol + "), 0) AS votos " +
                        "FROM Candidatos_eleccion ce " +
                        "JOIN Candidatos c ON c.ID_CANDIDATO = ce.ID_CANDIDATO " +
                        "LEFT JOIN Votos v ON v.ID_CANDIDATO = ce.ID_CANDIDATO AND v.ID_ELECCION = ce.ID_ELECCION " +
                        "WHERE ce.ID_ELECCION = ? " +
                        "GROUP BY c.PRIMER_NOMBRE, c.PRIMER_APELLIDO, ce.CARGO " +
                        "ORDER BY votos DESC";
                try (PreparedStatement ps = conn.prepareStatement(sqlResultados)) {
                    ps.setLong(1, idEleccion);
                    try (ResultSet rs = ps.executeQuery()) {
                        boolean first = true;
                        while (rs.next()) {
                            String c = rs.getString("candidato");
                            String cargo = rs.getString("CARGO");
                            double v = rs.getDouble("votos");
                            totalVotos += (long) v;
                            nCandidatos++;
                            if (first && v > 0) { ganador = c; first = false; }
                            filas.append("<tr class=\"")
                                 .append(ganador.equals(c) ? "winner" : "")
                                 .append("\"><td>").append(esc(c))
                                 .append("</td><td>").append(esc(cargo))
                                 .append("</td><td>").append(String.format("%.1f", v)).append("</td></tr>");
                        }
                    }
                }
            }

            String html = "<!DOCTYPE html>\n<html lang=\"es\">\n<head><meta charset=\"UTF-8\">" +
                    "<title>Acta de Ganadores - " + esc(nombre) + "</title>\n" +
                    "<style>" +
                    "*{margin:0;padding:0;box-sizing:border-box}" +
                    "body{font-family:'Segoe UI',system-ui,sans-serif;color:#1a3a2a;max-width:800px;margin:0 auto;padding:40px 24px}" +
                    ".header{text-align:center;border-bottom:3px solid #1a3a2a;padding-bottom:20px;margin-bottom:24px}" +
                    ".header h1{font-size:1.5rem;font-weight:800;letter-spacing:0.03em}" +
                    ".header p{color:#6b7280;font-size:0.9rem;margin-top:4px}" +
                    ".header .badge{display:inline-block;background:#1a3a2a;color:#fff;border-radius:6px;padding:4px 14px;font-size:0.75rem;font-weight:700;margin-top:8px}" +
                    "table{width:100%;border-collapse:collapse;margin:20px 0}" +
                    "th{background:#f8faf9;border-bottom:2px solid #d1d5db;color:#6b7280;font-size:0.72rem;font-weight:700;letter-spacing:0.05em;padding:10px 12px;text-align:left;text-transform:uppercase}" +
                    "td{border-bottom:1px solid #eee;padding:10px 12px;font-size:0.88rem}" +
                    "tr.winner td{background:#f0fdf4;font-weight:700}" +
                    "tr.winner td:first-child::before{content:\"\\2605 \";color:#158759}" +
                    ".totals{background:#f8faf9;border-radius:8px;padding:14px 18px;margin-top:16px;display:flex;gap:24px}" +
                    ".totals div strong{display:block;font-size:1.1rem;color:#1a3a2a}" +
                    ".totals div small{color:#6b7280;font-size:0.72rem}" +
                    ".footer{border-top:1px solid #eee;margin-top:32px;padding-top:16px;text-align:center;color:#9ca3af;font-size:0.75rem}" +
                    "@media print{body{padding:20px}.no-print{display:none}}" +
                    ".no-print{margin-top:20px;text-align:center}" +
                    ".no-print button{background:#1a3a2a;color:#fff;border:none;border-radius:8px;cursor:pointer;font-family:inherit;font-size:0.84rem;font-weight:700;padding:10px 24px}" +
                    "</style>\n</head>\n<body>\n" +
                    "<div class=\"header\"><h1>Acta de Ganadores</h1><p>" + esc(nombre) + "</p>" +
                    "<span class=\"badge\">" + esc(fecha) + "</span></div>\n" +
                    "<table><thead><tr><th>Candidato</th><th>Cargo</th><th>Votos</th></tr></thead>" +
                    "<tbody>" + filas.toString() + "</tbody></table>\n" +
                    "<div class=\"totals\">" +
                    "<div><small>Total votos</small><strong>" + totalVotos + "</strong></div>" +
                    "<div><small>Candidatos</small><strong>" + nCandidatos + "</strong></div>" +
                    "<div><small>Ganador</small><strong>" + esc(ganador.isEmpty() ? "Sin votos" : ganador) + "</strong></div>" +
                    "</div>\n" +
                    "<div class=\"footer\">ABIS-UPC · Sistema Electoral · Tribunal de Garantias Electorales</div>\n" +
                    "<div class=\"no-print\"><button onclick=\"window.print()\">Imprimir / Guardar como PDF</button></div>\n" +
                    "</body></html>";

            ctx.contentType("text/html; charset=UTF-8");
            ctx.result(html);
        } catch (NumberFormatException e) {
            ctx.status(400).json(ApiResponse.error("ID de eleccion invalido"));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    private static String esc(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
