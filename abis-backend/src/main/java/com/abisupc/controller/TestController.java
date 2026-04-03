package com.abisupc.controller;

import com.abisupc.integration.BiometricClient;
import io.javalin.Javalin;
import io.javalin.http.UploadedFile;

/**
 * ABIS-UPC | Capa de Controladores
 * Recibe peticiones HTTP del frontend y delega a la capa de integración.
 * Respeta el patrón: Controller → Business → Integration.
 */
public class TestController {

    public static void register(Javalin app) {

        // ── GET /api/health ────────────────────────────────────────────────
        // Verifica el estado de todas las capas del sistema
        app.get("/api/health", ctx -> {
            boolean pythonAlive = BiometricClient.isAlive();
            String status = pythonAlive
                ? "{\"backend\":\"ok\",\"biometric\":\"ok\",\"mensaje\":\"Todos los sistemas operativos\"}"
                : "{\"backend\":\"ok\",\"biometric\":\"offline\",\"mensaje\":\"Microservicio Python no responde en puerto 8000\"}";

            ctx.contentType("application/json").result(status);
        });

        // ── POST /api/ocr/scan ─────────────────────────────────────────────
        // Recibe la imagen del frontend, la reenvía a Python y retorna el OCR
        app.post("/api/ocr/scan", ctx -> {
            UploadedFile uploadedFile = ctx.uploadedFile("file");

            if (uploadedFile == null) {
                ctx.status(400)
                   .contentType("application/json")
                   .result("{\"error\":\"No se recibió ningún archivo. Campo esperado: 'file'\",\"fuente\":\"error\"}");
                return;
            }

            System.out.println("[TestController] Imagen recibida: "
                + uploadedFile.filename()
                + " (" + uploadedFile.size() + " bytes)"
                + " → reenviando a Python...");

            String resultado = BiometricClient.scanDocument(
                uploadedFile.content(),
                uploadedFile.filename()
            );

            System.out.println("[TestController] Respuesta Python: " + resultado);

            ctx.contentType("application/json").result(resultado);
        });
    }
}
