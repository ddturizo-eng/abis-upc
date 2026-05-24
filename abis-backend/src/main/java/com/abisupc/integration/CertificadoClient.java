package com.abisupc.integration;

import com.abisupc.dto.CertificadoEnvioRequest;
import com.abisupc.dto.CertificadoEnvioResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Cliente HTTP interno para el microservicio de certificados.
 */
public class CertificadoClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:8010";
    private static final String ENV_BASE_URL = "ABIS_EMAIL_SERVICE_URL";
    private static final String ENV_TOKEN = "ABIS_EMAIL_SERVICE_TOKEN";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final String internalToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CertificadoClient() {
        this(
                System.getenv().getOrDefault(ENV_BASE_URL, DEFAULT_BASE_URL),
                System.getenv(ENV_TOKEN),
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                new ObjectMapper()
        );
    }

    public CertificadoClient(String baseUrl, String internalToken, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = normalizarBaseUrl(baseUrl);
        this.internalToken = internalToken;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public CertificadoEnvioResponse enviar(CertificadoEnvioRequest payload) throws IOException {
        validarConfiguracion();
        validarPayload(payload);

        try {
            String json = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/certificados/enviar"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            CertificadoEnvioResponse parsed = parseResponse(response.body());
            if (response.statusCode() >= 200 && response.statusCode() < 300 && parsed.isSuccess()) {
                return parsed;
            }

            String detail = parsed.getError() != null ? parsed.getError() : response.body();
            throw new IOException("Servicio certificados HTTP " + response.statusCode() + ": " + detail);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrumpido enviando certificado", e);
        }
    }

    public boolean isAlive() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET();
            if (internalToken != null && !internalToken.isBlank()) {
                builder.header("X-Internal-Token", internalToken);
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private CertificadoEnvioResponse parseResponse(String body) throws IOException {
        if (body == null || body.isBlank()) {
            CertificadoEnvioResponse response = new CertificadoEnvioResponse();
            response.setSuccess(false);
            response.setError("Respuesta vacia del servicio de certificados");
            return response;
        }
        return objectMapper.readValue(body, CertificadoEnvioResponse.class);
    }

    private void validarConfiguracion() {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("Variable ABIS_EMAIL_SERVICE_TOKEN requerida");
        }
    }

    private void validarPayload(CertificadoEnvioRequest payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload requerido");
        }
        validarTexto(payload.getIdentificacion(), "identificacion");
        validarTexto(payload.getNombre(), "nombre");
        validarTexto(payload.getCorreo(), "correo");
        validarTexto(payload.getNombreEleccion(), "nombreEleccion");
        validarTexto(payload.getFechaVoto(), "fechaVoto");
        validarTexto(payload.getCodigoCertificado(), "codigoCertificado");
        validarTexto(payload.getNombrePuesto(), "nombrePuesto");
        validarTexto(payload.getSede(), "sede");
        validarTexto(payload.getCiudad(), "ciudad");
        if (payload.getIdEleccion() == null || payload.getIdEleccion() <= 0) {
            throw new IllegalArgumentException("idEleccion requerido");
        }
    }

    private void validarTexto(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " requerido");
        }
    }

    private String normalizarBaseUrl(String value) {
        String url = value == null || value.isBlank() ? DEFAULT_BASE_URL : value.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
