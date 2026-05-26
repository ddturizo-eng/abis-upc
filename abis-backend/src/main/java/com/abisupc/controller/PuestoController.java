package com.abisupc.controller;

import com.abisupc.model.PuestoVotacion;
import com.abisupc.repository.PuestoVotacionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PuestoController {

    private static final PuestoVotacionRepository repo = new PuestoVotacionRepository();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void getAll(Context ctx) {
        try {
            ctx.json(mapper.writeValueAsString(repo.findAll()));
        } catch (Exception e) {
            ctx.status(500).json("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public static void create(Context ctx) {
        try {
            ObjectNode body = mapper.readValue(ctx.body(), ObjectNode.class);
            PuestoVotacion puesto = parsePuesto(body, null);
            repo.save(puesto);
            ctx.status(201).json("{\"success\":true,\"message\":\"Puesto creado\"}");
        } catch (IllegalArgumentException e) {
            ctx.status(400).json("{\"error\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            ctx.status(500).json("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public static void update(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            ObjectNode body = mapper.readValue(ctx.body(), ObjectNode.class);
            PuestoVotacion puesto = parsePuesto(body, id);
            repo.update(puesto);
            ctx.json("{\"success\":true,\"message\":\"Puesto actualizado\"}");
        } catch (IllegalArgumentException e) {
            ctx.status(400).json("{\"error\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            ctx.status(500).json("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public static void delete(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            repo.delete(id);
            ctx.json("{\"success\":true,\"message\":\"Puesto eliminado\"}");
        } catch (Exception e) {
            ctx.status(500).json("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private static PuestoVotacion parsePuesto(ObjectNode body, Long id) {
        String ciudad = body.has("ciudad") ? body.get("ciudad").asText() : null;
        String sede = body.has("sede") ? body.get("sede").asText() : null;
        String nombre = body.has("nombrePuesto") ? body.get("nombrePuesto").asText() : null;
        String inicio = body.has("horaInicio") ? body.get("horaInicio").asText() : null;
        String salida = body.has("horaSalida") ? body.get("horaSalida").asText() : null;

        if (ciudad == null || ciudad.isBlank()) throw new IllegalArgumentException("Ciudad requerida");
        if (sede == null || sede.isBlank()) throw new IllegalArgumentException("Sede requerida");
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("Nombre del puesto requerido");
        if (inicio == null || inicio.isBlank()) throw new IllegalArgumentException("Hora de inicio requerida");
        if (salida == null || salida.isBlank()) throw new IllegalArgumentException("Hora de salida requerida");

        Timestamp tsInicio = Timestamp.valueOf(LocalDateTime.parse(inicio, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        Timestamp tsSalida = Timestamp.valueOf(LocalDateTime.parse(salida, DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        if (!tsSalida.after(tsInicio)) throw new IllegalArgumentException("La hora de salida debe ser posterior a la de inicio");

        PuestoVotacion puesto = new PuestoVotacion();
        if (id != null) puesto.setId(id);
        puesto.setCiudad(ciudad.trim());
        puesto.setSede(sede.trim());
        puesto.setNombrePuesto(nombre.trim());
        puesto.setHoraInicio(tsInicio);
        puesto.setHoraSalida(tsSalida);
        return puesto;
    }
}