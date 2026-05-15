package com.abisupc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class BiometricService {

    private static final String FASTAPI_URL =
        System.getenv().getOrDefault("BIOMETRIC_SERVICE_URL", "http://localhost:8001");

    private static final String NATIVE_URL =
        System.getenv().getOrDefault("NATIVE_SERVICE_URL", "http://localhost:8765");

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode safeParseJson(String body, int statusCode) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            System.err.println("[BiometricService] Respuesta no-JSON de FastAPI (status " + statusCode + "): " + body);
            ObjectNode node = mapper.createObjectNode();
            node.put("error", "FastAPI error " + statusCode + ": " + body);
            node.put("success", false);
            node.put("matched", false);
            return node;
        }
    }

    public JsonNode enroll(String identificacion, boolean reEnroll) throws Exception {
        var bodyMap = Map.of(
            "identificacion", identificacion,
            "re_enroll", reEnroll
        );
        var bodyStr = mapper.writeValueAsString(bodyMap);
        var bodyBytes = bodyStr.getBytes(StandardCharsets.UTF_8);

        System.out.println("[BiometricService] POST /enroll -> FastAPI: " + bodyStr);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(FASTAPI_URL + "/enroll/"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("[BiometricService] FastAPI status: " + response.statusCode());
        System.out.println("[BiometricService] FastAPI response: " + response.body());
        return safeParseJson(response.body(), response.statusCode());
    }

    public JsonNode enrollProgress() throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(FASTAPI_URL + "/enroll/progress"))
                .header("Accept", "application/json")
                .GET()
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return safeParseJson(response.body(), response.statusCode());
    }

    public JsonNode verify() throws Exception {
        System.out.println("[BiometricService] POST /verify -> FastAPI");

        var request = HttpRequest.newBuilder()
                .uri(URI.create(FASTAPI_URL + "/verify/"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("[BiometricService] FastAPI status: " + response.statusCode());
        System.out.println("[BiometricService] FastAPI response: " + response.body());
        return safeParseJson(response.body(), response.statusCode());
    }

    public JsonNode servicesStatus() {
        ObjectNode result = mapper.createObjectNode();
        try {
            var r = client.send(HttpRequest.newBuilder()
                            .uri(URI.create(FASTAPI_URL + "/status"))
                            .version(HttpClient.Version.HTTP_1_1).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            result.put("fastapi", r.statusCode() == 200 ? "ok" : "error");
        } catch (Exception e) {
            result.put("fastapi", "error");
        }
        try {
            var r = client.send(HttpRequest.newBuilder()
                            .uri(URI.create(NATIVE_URL + "/status"))
                            .version(HttpClient.Version.HTTP_1_1).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            result.put("native", r.statusCode() == 200 ? "ok" : "error");
        } catch (Exception e) {
            result.put("native", "error");
        }
        return result;
    }
}
