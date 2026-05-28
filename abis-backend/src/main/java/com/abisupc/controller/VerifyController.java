package com.abisupc.controller;

import com.abisupc.service.BiometricService;
import io.javalin.http.Context;

/**
 * Endpoint de verificacion de huella dactilar.
 *
 * <p>Delega la verificacion biometrica al servicio de Python y retorna
 * el resultado al cliente para validar la identidad del votante.
 */
public class VerifyController {

    private static final BiometricService service = new BiometricService();

    /**
     * Verifica la identidad de un votante mediante su huella dactilar.
     *
     * <p>Delega la comparacion de la huella capturada contra las plantillas
     * almacenadas al microservicio biometrico de Python.
     *
     * @param ctx contexto HTTP
     */
    public static void verify(Context ctx) {
        try {
            System.out.println("[VerifyController] Verificando huella...");
            var result = service.verify();
            ctx.json(result.toString());

        } catch (Exception e) {
            System.err.println("[VerifyController] Error: " + e.getMessage());
            ctx.status(500).json("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
