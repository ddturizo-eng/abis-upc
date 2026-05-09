package com.abisupc.controller;

import com.abisupc.dto.ApiResponse;
import com.abisupc.integration.BiometricClient;
import com.abisupc.model.Votante;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.UploadedFile;

/**
 * ABIS-UPC | Capa de Controladores
 * Recibe peticiones HTTP del frontend y delega a la capa de integracion.
 * Respeta el patron: Controller -> Business -> Integration.
 */
public class TestController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void register(Javalin app) {

        // ── GET /api/health ────────────────────────────────────────────────
        app.get("/api/health", ctx -> {
            boolean pythonAlive = BiometricClient.isAlive();
            boolean ocrAlive = BiometricClient.isOcrAlive();
            String status = (pythonAlive && ocrAlive)
                ? "{\"backend\":\"ok\",\"biometric\":\"ok\",\"ocr\":\"ok\",\"mensaje\":\"Todos los sistemas operativos\"}"
                : "{\"backend\":\"ok\",\"biometric\":\"" + (pythonAlive ? "ok" : "offline") + "\",\"ocr\":\"" + (ocrAlive ? "ok" : "offline") + "\",\"mensaje\":\"Verificar puertos 8001 (biometrico) y 8002 (OCR)\"}";

            ctx.contentType("application/json").result(status);
        });

        // ── POST /api/ocr/scan ─────────────────────────────────────────────
        app.post("/api/ocr/scan", ctx -> {
            UploadedFile uploadedFile = ctx.uploadedFile("file");

            if (uploadedFile == null) {
                ctx.status(400)
                   .contentType("application/json")
                   .result("{\"error\":\"No se recibio ningun archivo. Campo esperado: 'file'\",\"fuente\":\"error\"}");
                return;
            }

            System.out.println("[TestController] Imagen recibida: "
                + uploadedFile.filename()
                + " (" + uploadedFile.size() + " bytes)"
                + " -> reenviando a OCR...");

            String resultado = BiometricClient.scanDocument(
                uploadedFile.content(),
                uploadedFile.filename()
            );

            System.out.println("[TestController] Respuesta OCR: " + resultado);
            ctx.contentType("application/json").result(resultado);
        });

        // ── POST /api/enroll ───────────────────────────────────────────────
        app.post("/api/enroll", ctx -> {
            String body = ctx.body();
            try {
                var node = MAPPER.readTree(body);
                String identificacion = node.get("identificacion").asText();
                boolean reEnroll = node.has("re_enroll") && node.get("re_enroll").asBoolean();

                String resultado = BiometricClient.enrollVoter(identificacion, reEnroll);
                System.out.println("[TestController] Respuesta enroll: " + resultado);
                ctx.contentType("application/json").result(resultado);
            } catch (Exception e) {
                ctx.status(400)
                   .contentType("application/json")
                   .result("{\"success\":false,\"error\":\"Body invalido: " + e.getMessage() + "\"}");
            }
        });

        // ── POST /api/verify ───────────────────────────────────────────────
        app.post("/api/verify", ctx -> {
            String resultado = BiometricClient.verifyFingerprint();
            System.out.println("[TestController] Respuesta verify: " + resultado);
            ctx.contentType("application/json").result(resultado);
        });

        // ── POST /api/vote ─────────────────────────────────────────────────
        app.post("/api/vote", ctx -> {
            String body = ctx.body();
            try {
                var node = MAPPER.readTree(body);
                String identificacion = node.get("identificacion").asText();

                String resultado = BiometricClient.registerVote(identificacion);
                System.out.println("[TestController] Respuesta vote: " + resultado);
                ctx.contentType("application/json").result(resultado);
            } catch (Exception e) {
                ctx.status(400)
                   .contentType("application/json")
                   .result("{\"success\":false,\"error\":\"Body invalido: " + e.getMessage() + "\"}");
            }
        });
    }
}
