package com.abisupc.integration;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class QrRenderClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:8001";
    private static final String ENV_BASE_URL = "BIOMETRIC_SERVICE_URL";
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public QrRenderClient() {
        this(System.getenv().getOrDefault(ENV_BASE_URL, DEFAULT_BASE_URL),
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .version(java.net.http.HttpClient.Version.HTTP_1_1)
                        .build(),
                new ObjectMapper());
    }

    public QrRenderClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = normalizarBaseUrl(baseUrl);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public byte[] render(String value) throws IOException {
        // Normalizar: eliminar cualquier caracter no imprimible que cause rechazo en Pydantic
        String valorLimpio = value == null ? "" : value
                .replace("\u0000", "")
                .replace("\r", "")
                .replace("\n", "")
                .replaceAll("\\s+", "")
                .trim()
                .toUpperCase();

        if (valorLimpio.isBlank()) {
            throw new IOException("Valor de QR vacio despues de normalizar");
        }

        try {
            String json = objectMapper.writeValueAsString(Map.of("value", valorLimpio));
            System.out.println("[QrRenderClient] POST " + baseUrl + "/qr/render  body=" + json);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/qr/render"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "image/png, application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            System.out.println("[QrRenderClient] respuesta HTTP " + response.statusCode()
                    + " bytes=" + response.body().length);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            String bodyStr = new String(response.body());
            throw new IOException("Servicio QR HTTP " + response.statusCode() + " - " + bodyStr);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrumpido generando QR", e);
        }
    }

    private String normalizarBaseUrl(String value) {
        String url = value == null || value.isBlank() ? DEFAULT_BASE_URL : value.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
