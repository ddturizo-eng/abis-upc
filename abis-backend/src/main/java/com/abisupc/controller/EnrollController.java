package com.abisupc.controller;

import com.abisupc.model.EnrollRequest;
import com.abisupc.service.BiometricService;
import io.javalin.http.Context;

public class EnrollController {

    private static final BiometricService service = new BiometricService();

    public static void enroll(Context ctx) {
        try {
            System.out.println("[EnrollController] Body recibido: " + ctx.body());
            var body = ctx.bodyAsClass(EnrollRequest.class);

            if (body.identificacion == null || body.identificacion.isBlank()) {
                ctx.status(400).json("{\"error\":\"identificacion requerida\"}");
                return;
            }

            var result = service.enroll(body.identificacion, body.re_enroll);
            if (!result.path("success").asBoolean(false)) {
                ctx.status(result.hasNonNull("detail") ? 503 : 502);
            }
            ctx.contentType("application/json").result(result.toString());

        } catch (Exception e) {
            System.err.println("[EnrollController] Error: " + e.getMessage());
            ctx.status(500).json("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public static void progress(Context ctx) {
        try {
            ctx.contentType("application/json").result(service.enrollProgress().toString());
        } catch (Exception e) {
            System.err.println("[EnrollController] Progress error: " + e.getMessage());
            ctx.status(503).json("{\"error\":\"No se pudo consultar el progreso biometrico\"}");
        }
    }
}
