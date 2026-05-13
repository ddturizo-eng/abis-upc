package com.abisupc.controller;

import com.abisupc.dto.ApiResponse;
import com.abisupc.model.Candidato;
import com.abisupc.repository.CandidatoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;

public class CandidatoController {

    private static final CandidatoRepository candidatoRepo = new CandidatoRepository();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void getByEleccion(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(ApiResponse.success(candidatoRepo.findByEleccion(id)));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void agregar(Context ctx) {
        try {
            Long idEleccion = Long.parseLong(ctx.pathParam("id"));
            JsonNode body = mapper.readTree(ctx.body());
            Candidato candidato = parseCandidato(body);
            Integer numeroCampania = parseNumeroCampania(body);
            String cargo = text(body, "cargo");
            if (cargo == null || cargo.isBlank()) {
                throw new IllegalArgumentException("Cargo requerido");
            }

            Long idCandidato = candidatoRepo.savePersona(candidato);
            candidatoRepo.savePostulacion(idCandidato, idEleccion, numeroCampania, cargo.trim());
            ctx.status(201).json(ApiResponse.success("Candidato agregado"));
        } catch (IllegalArgumentException e) {
            ctx.status(409).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void editar(Context ctx) {
        try {
            Long idEleccion = Long.parseLong(ctx.pathParam("idEleccion"));
            Long idCandidato = Long.parseLong(ctx.pathParam("idCandidato"));
            JsonNode body = mapper.readTree(ctx.body());
            Integer numeroCampania = parseNumeroCampania(body);
            String cargo = text(body, "cargo");
            if (cargo == null || cargo.isBlank()) {
                throw new IllegalArgumentException("Cargo requerido");
            }

            candidatoRepo.updatePostulacion(idCandidato, idEleccion, numeroCampania, cargo.trim());
            ctx.json(ApiResponse.success("Candidato actualizado"));
        } catch (IllegalArgumentException e) {
            ctx.status(409).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void eliminar(Context ctx) {
        try {
            Long idEleccion = Long.parseLong(ctx.pathParam("idEleccion"));
            Long idCandidato = Long.parseLong(ctx.pathParam("idCandidato"));
            if (candidatoRepo.tieneVotos(idCandidato, idEleccion)) {
                ctx.status(409).json(ApiResponse.error("No se puede eliminar: tiene votos asociados"));
                return;
            }

            candidatoRepo.deletePostulacion(idCandidato, idEleccion);
            ctx.json(ApiResponse.success("Candidato eliminado"));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    private static Candidato parseCandidato(JsonNode body) {
        String primerNombre = text(body, "primerNombre");
        String segundoNombre = text(body, "segundoNombre");
        String primerApellido = text(body, "primerApellido");
        String segundoApellido = text(body, "segundoApellido");

        if (primerNombre == null || primerNombre.isBlank()) {
            throw new IllegalArgumentException("Primer nombre requerido");
        }
        if (primerApellido == null || primerApellido.isBlank()) {
            throw new IllegalArgumentException("Primer apellido requerido");
        }

        Candidato candidato = new Candidato();
        candidato.setPrimerNombre(primerNombre.trim());
        candidato.setSegundoNombre(segundoNombre != null ? segundoNombre.trim() : null);
        candidato.setPrimerApellido(primerApellido.trim());
        candidato.setSegundoApellido(segundoApellido != null ? segundoApellido.trim() : null);
        return candidato;
    }

    private static Integer parseNumeroCampania(JsonNode body) {
        String numeroCampania = text(body, "numeroCampania");
        int numero;
        try {
            numero = Integer.parseInt(numeroCampania);
        } catch (Exception e) {
            throw new IllegalArgumentException("Número de campaña requerido");
        }
        if (numero <= 0) {
            throw new IllegalArgumentException("Número de campaña debe ser mayor que 0");
        }
        return numero;
    }

    private static String text(JsonNode body, String field) {
        return body != null && body.hasNonNull(field) ? body.get(field).asText() : null;
    }
}
