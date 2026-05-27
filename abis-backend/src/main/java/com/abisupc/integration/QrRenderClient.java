package com.abisupc.integration;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Cliente HTTP para el endpoint de renderizado de codigos QR del microservicio
 * biometrico (FastAPI :8001).
 *
 * <p>Genera la imagen PNG del QR a partir de un valor de texto, normalizado
 * antes de enviarlo para evitar caracteres invalidos que Pydantic rechazaria.
 * El resultado se usa en el flujo de contingencia para incluir el QR del token
 * en el correo del votante.
 *
 * <p>Variables de entorno:
 * <ul>
 *   <li>{@code BIOMETRIC_SERVICE_URL} — URL base del servicio (default: {@code http://localhost:8001})</li>
 * </ul>
 */
public class QrRenderClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:8001";
    private static final String ENV_BASE_URL = "BIOMETRIC_SERVICE_URL";
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructor por defecto. Lee la URL base de la variable de entorno
     * {@code BIOMETRIC_SERVICE_URL} o usa {@code http://localhost:8001}.
     */
    public QrRenderClient() {
        this(System.getenv().getOrDefault(ENV_BASE_URL, DEFAULT_BASE_URL),
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .version(java.net.http.HttpClient.Version.HTTP_1_1)
                        .build(),
                new ObjectMapper());
    }

    /**
     * Constructor para inyeccion de dependencias (util en pruebas).
     *
     * @param baseUrl      URL base del microservicio biometrico
     * @param httpClient   cliente HTTP a usar
     * @param objectMapper serializador JSON
     */
    public QrRenderClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = normalizarBaseUrl(baseUrl);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Genera la imagen PNG de un codigo QR a partir de un valor de texto.
     *
     * <p>Normaliza el valor antes de enviarlo: elimina caracteres nulos,
     * saltos de linea y espacios, y convierte a mayusculas. Esto evita que
     * Pydantic rechace el request por caracteres invalidos en el campo
     * {@code value} del esquema FastAPI.
     *
     * @param value texto a codificar en el QR (ej: token de contingencia)
     * @return imagen PNG como arreglo de bytes lista para incrustar en un PDF
     * @throws IOException si el microservicio retorna error HTTP,
     *                     si el valor queda vacio despues de normalizar,
     *                     o si la conexion falla
     */
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

    /**
     * Normaliza la URL base eliminando la barra final si existe.
     *
     * @param value URL a normalizar
     * @return URL sin barra final
     */
    private String normalizarBaseUrl(String value) {
        String url = value == null || value.isBlank() ? DEFAULT_BASE_URL : value.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}