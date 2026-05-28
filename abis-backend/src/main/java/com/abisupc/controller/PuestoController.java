package com.abisupc.controller;

import com.abisupc.repository.PuestoVotacionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;

/**
 * Endpoint para consultar puestos de votacion.
 *
 * <p>Retorna la lista completa de puestos de votacion registrados en el sistema.
 */
public class PuestoController {

    private static final PuestoVotacionRepository repo = new PuestoVotacionRepository();
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Lista todos los puestos de votacion registrados.
     *
     * @param ctx contexto HTTP
     */
    public static void getAll(Context ctx) {
        try {
            var puestos = repo.findAll();
            ctx.json(mapper.writeValueAsString(puestos));
        } catch (Exception e) {
            ctx.status(500).json("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}