package com.abisupc.controller;

import com.abisupc.dto.ApiResponse;
import com.abisupc.model.Eleccion;
import com.abisupc.model.EstadoEleccion;
import com.abisupc.repository.EleccionRepository;
import com.abisupc.repository.EleccionRolRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EleccionController {

    private static final EleccionRepository eleccionRepo = new EleccionRepository();
    private static final EleccionRolRepository eleccionRolRepo = new EleccionRolRepository();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void getAll(Context ctx) {
        try {
            List<Map<String, Object>> elecciones = eleccionRepo.findAll().stream()
                    .map(EleccionController::toResponse)
                    .toList();
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
            ctx.status(201).json(ApiResponse.success(toResponse(eleccion)));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
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
            if (eleccionRepo.hayEleccionEnCurso()) {
                ctx.status(409).json(ApiResponse.error("Ya existe una elección en curso"));
                return;
            }

            Eleccion eleccion = eleccionRepo.findById(id).orElse(null);
            if (eleccion == null || eleccion.getEstado() != EstadoEleccion.PROGRAMADA) {
                ctx.status(409).json(ApiResponse.error("La elección debe estar en estado PROGRAMADA"));
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

            eleccionRepo.cambiarEstado(id, "CERRADA");
            ctx.json(ApiResponse.success("Elección cerrada"));
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

            eleccionRolRepo.save(idEleccion, idRol, pesoVoto);
            ctx.status(201).json(ApiResponse.success("Peso de voto configurado"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
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

    private static Map<String, Object> toResponse(Eleccion eleccion) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", eleccion.getId());
        data.put("nombre", eleccion.getNombre());
        data.put("fechaHoraInicio", eleccion.getFechaHoraInicio() != null ? eleccion.getFechaHoraInicio().toString() : null);
        data.put("fechaHoraFin", eleccion.getFechaHoraFin() != null ? eleccion.getFechaHoraFin().toString() : null);
        data.put("estado", eleccion.getEstado() != null ? eleccion.getEstado().getDbValue() : null);
        return data;
    }
}
