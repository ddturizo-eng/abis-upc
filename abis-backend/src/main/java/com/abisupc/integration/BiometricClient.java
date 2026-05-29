package com.abisupc.integration;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;

import java.io.InputStream;

/**
 * ABIS-UPC | Capa de Integracion
 * Responsable de comunicarse con los microservicios Python:
 *   - OCR       (:8002) — escaneo de documentos
 *   - Biometrico (:8001) — enrolamiento, verificacion, voto
 */
public class BiometricClient {

    private static final String BIOMETRIC_BASE_URL =
        System.getenv().getOrDefault("BIOMETRIC_SERVICE_URL", "http://localhost:8001");

    private static final String OCR_BASE_URL =
        System.getenv().getOrDefault("OCR_SERVICE_URL", "http://localhost:8002");



    public static String scanDocument(InputStream imageStream, String filename) {
        try {
            HttpResponse<JsonNode> response = Unirest
                    .post(OCR_BASE_URL + "/scan")
                    .field("file", imageStream, filename)
                    .asJson();

            if (response.getStatus() == 200) {
                return response.getBody().toString();
            }

            return String.format(
                "{\"error\": \"Microservicio OCR retorno HTTP %d\", \"fuente\": \"error\"}",
                response.getStatus()
            );

        } catch (Exception e) {
            System.err.println("[BiometricClient] Error conectando a OCR: " + e.getMessage());
            return "{\"error\": \"No se pudo conectar al microservicio OCR. Esta corriendo en puerto 8002?\", \"fuente\": \"error\"}";
        }
    }

  
    public static String enrollVoter(String identificacion, boolean reEnroll) {
        try {
            String body = String.format(
                "{\"identificacion\":\"%s\",\"re_enroll\":%s}",
                identificacion, reEnroll
            );
            HttpResponse<JsonNode> response = Unirest
                    .post(BIOMETRIC_BASE_URL + "/enroll/")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .asJson();

            return response.getBody().toString();

        } catch (Exception e) {
            System.err.println("[BiometricClient] Error conectando a biometrico (enroll): " + e.getMessage());
            return "{\"success\":false,\"error\":\"No se pudo conectar al microservicio biometrico. Esta corriendo en puerto 8001?\"}";
        }
    }

  
    public static String verifyFingerprint() {
        try {
            HttpResponse<JsonNode> response = Unirest
                    .post(BIOMETRIC_BASE_URL + "/verify/")
                    .asJson();

            return response.getBody().toString();

        } catch (Exception e) {
            System.err.println("[BiometricClient] Error conectando a biometrico (verify): " + e.getMessage());
            return "{\"matched\":false,\"error\":\"No se pudo conectar al microservicio biometrico. Esta corriendo en puerto 8001?\"}";
        }
    }



    public static String registerVote(String identificacion) {
        try {
            String body = String.format("{\"identificacion\":\"%s\"}", identificacion);
            HttpResponse<JsonNode> response = Unirest
                    .post(BIOMETRIC_BASE_URL + "/vote/")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .asJson();

            return response.getBody().toString();

        } catch (Exception e) {
            System.err.println("[BiometricClient] Error conectando a biometrico (vote): " + e.getMessage());
            return "{\"success\":false,\"error\":\"No se pudo conectar al microservicio biometrico. Esta corriendo en puerto 8001?\"}";
        }
    }

  

    public static boolean isAlive() {
        try {
            int status = Unirest.get(BIOMETRIC_BASE_URL + "/health").asJson().getStatus();
            return status == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isOcrAlive() {
        try {
            int status = Unirest.get(OCR_BASE_URL + "/health").asJson().getStatus();
            return status == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
