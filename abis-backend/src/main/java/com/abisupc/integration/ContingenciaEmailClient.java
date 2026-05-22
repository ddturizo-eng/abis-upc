package com.abisupc.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class ContingenciaEmailClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:8010";
    private static final String ENV_BASE_URL = "ABIS_EMAIL_SERVICE_URL";
    private static final String ENV_TOKEN = "ABIS_EMAIL_SERVICE_TOKEN";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String baseUrl;
    private final String internalToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ContingenciaEmailClient() {
        this(System.getenv().getOrDefault(ENV_BASE_URL, DEFAULT_BASE_URL),
                System.getenv(ENV_TOKEN),
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .version(java.net.http.HttpClient.Version.HTTP_1_1)
                        .build(),
                new ObjectMapper());
    }

    public ContingenciaEmailClient(String baseUrl, String internalToken, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = normalizarBaseUrl(baseUrl);
        this.internalToken = internalToken;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> enviarQr(Map<String, Object> payload) throws IOException {
        validarConfiguracion();
        try {
            String json = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/contingencia/enviar-qr"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode parsed = response.body() == null || response.body().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(response.body());
            if (response.statusCode() >= 200 && response.statusCode() < 300 && parsed.path("success").asBoolean(false)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("messageId", parsed.path("messageId").asText(null));
                result.put("status", parsed.path("status").asText("ENVIADO"));
                return result;
            }
            String detail = parsed.path("error").asText(response.body());
            throw new IOException("Servicio email HTTP " + response.statusCode() + ": " + detail);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrumpido enviando QR de contingencia", e);
        }
    }

    private void validarConfiguracion() {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("Variable ABIS_EMAIL_SERVICE_TOKEN requerida");
        }
    }

    private String normalizarBaseUrl(String value) {
        String url = value == null || value.isBlank() ? DEFAULT_BASE_URL : value.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
