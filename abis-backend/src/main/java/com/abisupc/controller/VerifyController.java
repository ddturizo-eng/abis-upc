package com.abisupc.controller;

import com.abisupc.service.BiometricService;
import io.javalin.http.Context;

public class VerifyController {

    private static final BiometricService service = new BiometricService();

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
