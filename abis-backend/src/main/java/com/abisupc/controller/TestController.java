package com.abisupc.controller;

import com.abisupc.integration.BiometricClient;
import io.javalin.Javalin;

/**
 * ABIS-UPC | Capa de Controladores
 * Health check y status de todos los servicios del sistema.
 */
public class TestController {

    public static void register(Javalin app) {

        app.get("/api/health", ctx -> {
            boolean pythonAlive = BiometricClient.isAlive();
            boolean ocrAlive = BiometricClient.isOcrAlive();
            String status = (pythonAlive && ocrAlive)
                ? "{\"backend\":\"ok\",\"biometric\":\"ok\",\"ocr\":\"ok\",\"mensaje\":\"Todos los sistemas operativos\"}"
                : "{\"backend\":\"ok\",\"biometric\":\"" + (pythonAlive ? "ok" : "offline") + "\",\"ocr\":\"" + (ocrAlive ? "ok" : "offline") + "\",\"mensaje\":\"Verificar puertos 8001 (biometrico) y 8002 (OCR)\"}";

            ctx.contentType("application/json").result(status);
        });
    }
}
