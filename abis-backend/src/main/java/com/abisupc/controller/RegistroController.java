package com.abisupc.controller;

import com.abisupc.model.EstadoVotante;
import com.abisupc.model.RegistroRequest;
import com.abisupc.model.Votante;
import com.abisupc.repository.VotanteRepository;
import io.javalin.http.Context;

public class RegistroController {

    private static final VotanteRepository repository = new VotanteRepository();

    public static void crear(Context ctx) {
        try {
            var body = ctx.bodyAsClass(RegistroRequest.class);

            if (body.identificacion == null || body.identificacion.isBlank()) {
                ctx.status(400).json("{\"error\":\"identificacion requerida\"}");
                return;
            }
            if (body.primerNombre == null || body.primerNombre.isBlank()) {
                ctx.status(400).json("{\"error\":\"primer_nombre requerido\"}");
                return;
            }
            if (body.primerApellido == null || body.primerApellido.isBlank()) {
                ctx.status(400).json("{\"error\":\"primer_apellido requerido\"}");
                return;
            }
            if (body.correo == null || body.correo.isBlank()) {
                ctx.status(400).json("{\"error\":\"correo requerido\"}");
                return;
            }
            if (body.idRol == null) {
                ctx.status(400).json("{\"error\":\"id_rol requerido\"}");
                return;
            }
            if (body.idPuesto == null) {
                ctx.status(400).json("{\"error\":\"id_puesto requerido\"}");
                return;
            }

            var existente = repository.findByIdentificacion(body.identificacion);
            if (existente.isPresent()) {
                ctx.status(409).json("{\"error\":\"Votante ya registrado\"}");
                return;
            }

            var porCorreo = repository.findByCorreo(body.correo);
            if (porCorreo.isPresent()) {
                ctx.status(409).json("{\"error\":\"Correo ya registrado\"}");
                return;
            }

            Votante votante = new Votante();
            votante.setIdentificacion(body.identificacion);
            votante.setPrimerNombre(body.primerNombre);
            votante.setSegundoNombre(body.segundoNombre);
            votante.setPrimerApellido(body.primerApellido);
            votante.setSegundoApellido(body.segundoApellido);
            votante.setCorreo(body.correo);
            votante.setEstadoVoto(EstadoVotante.PENDIENTE.name());
            votante.setIdRol(body.idRol);
            votante.setIdPuesto(body.idPuesto);

            repository.save(votante);

            String nombreCompleto = body.primerNombre + " " + body.primerApellido;

            ctx.status(201).json("{\"success\":true,\"identificacion\":\""
                    + body.identificacion + "\",\"nombre_completo\":\""
                    + nombreCompleto + "\",\"message\":\"Votante registrado exitosamente\"}");

            System.out.println("[RegistroController] Votante registrado: " + body.identificacion);

        } catch (RuntimeException e) {
            System.err.println("[RegistroController] Error: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("identificaci")) {
                ctx.status(409).json("{\"error\":\"Votante ya registrado\"}");
            } else if (e.getMessage() != null && e.getMessage().contains("UNIQUE") || 
                       (e.getMessage() != null && e.getMessage().contains("CORREO"))) {
                ctx.status(409).json("{\"error\":\"Correo ya registrado\"}");
            } else {
                ctx.status(500).json("{\"error\":\"" + e.getMessage() + "\"}");
            }
        } catch (Exception e) {
            System.err.println("[RegistroController] Error: " + e.getMessage());
            ctx.status(500).json("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}