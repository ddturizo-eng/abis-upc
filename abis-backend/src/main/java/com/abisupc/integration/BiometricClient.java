package com.abisupc.integration;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;

import java.io.InputStream;

/**
 * ABIS-UPC | Capa de Integración
 * Responsable de comunicarse con el microservicio Python (puerto 8000).
 * Usa Unirest para enviar la imagen como POST multipart/form-data.
 */
public class BiometricClient {

    private static final String BIOMETRIC_BASE_URL = "http://localhost:8000";

    /**
     * Envía una imagen al endpoint /ocr/scan del microservicio Python.
     *
     * @param imageStream Stream de bytes de la imagen recibida del frontend.
     * @param filename    Nombre original del archivo (ej: "cedula.png").
     * @return JSON string con el resultado del OCR (o un JSON de error).
     */
    public static String scanDocument(InputStream imageStream, String filename) {
        try {
            HttpResponse<JsonNode> response = Unirest
                    .post(BIOMETRIC_BASE_URL + "/ocr/scan")
                    .field("file", imageStream, filename)
                    .asJson();

            if (response.getStatus() == 200) {
                return response.getBody().toString();
            }

            return String.format(
                "{\"error\": \"Microservicio Python retornó HTTP %d\", \"fuente\": \"error\"}",
                response.getStatus()
            );

        } catch (Exception e) {
            System.err.println("[BiometricClient] Error conectando a Python: " + e.getMessage());
            return "{\"error\": \"No se pudo conectar al microservicio biométrico. ¿Está corriendo en puerto 8000?\", \"fuente\": \"error\"}";
        }
    }

    /**
     * Verifica si el microservicio Python está activo.
     * @return true si responde en /health, false en caso contrario.
     */
    public static boolean isAlive() {
        try {
            int status = Unirest.get(BIOMETRIC_BASE_URL + "/health").asJson().getStatus();
            return status == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
