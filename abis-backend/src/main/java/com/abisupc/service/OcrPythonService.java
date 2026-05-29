package com.abisupc.service;

import com.abisupc.model.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OcrPythonService {

    private static final String OCR_URL =
        System.getenv().getOrDefault("OCR_SERVICE_URL", "http://localhost:8002");

    private final String pythonApiUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OcrPythonService() {
        this.pythonApiUrl = OCR_URL;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public OcrPythonService(String pythonApiUrl) {
        this.pythonApiUrl = pythonApiUrl != null ? pythonApiUrl : OCR_URL;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public ScanResult scan(byte[] frontImage, byte[] backImage, String docType) throws IOException {
        try {
            String boundary = "----Boundary" + UUID.randomUUID().toString().replace("-", "");
            String url = pythonApiUrl + "/scan";

            byte[] multipartBody = buildMultipart(boundary, frontImage, backImage, docType);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("[OCR] Python API status: " + response.statusCode());
            System.out.println("[OCR] Python API response: " + response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), ScanResult.class);
            } else {
                System.err.println("Python API error: " + response.statusCode() + " - " + response.body());
                return null;
            }
        } catch (Exception e) {
            System.err.println("Error calling Python OCR API: " + e.getMessage());
            throw new IOException("Error calling Python OCR API: " + e.getMessage(), e);
        }
    }

    private byte[] buildMultipart(String boundary, byte[] frontImage, byte[] backImage, String docType) throws IOException {
        List<byte[]> parts = new ArrayList<>();
        String CRLF = "\r\n";
        String delimiter = "--" + boundary + CRLF;
        String closing  = "--" + boundary + "--" + CRLF;

        parts.add((delimiter
                + "Content-Disposition: form-data; name=\"doc_type\"" + CRLF
                + CRLF
                + docType + CRLF
        ).getBytes());

        parts.add((delimiter
                + "Content-Disposition: form-data; name=\"front\"; filename=\"front.jpg\"" + CRLF
                + "Content-Type: image/jpeg" + CRLF
                + CRLF
        ).getBytes());
        parts.add(frontImage);
        parts.add(CRLF.getBytes());

        if (backImage != null && backImage.length > 0) {
            parts.add((delimiter
                    + "Content-Disposition: form-data; name=\"back\"; filename=\"back.jpg\"" + CRLF
                    + "Content-Type: image/jpeg" + CRLF
                    + CRLF
            ).getBytes());
            parts.add(backImage);
            parts.add(CRLF.getBytes());
        }

        parts.add(closing.getBytes());

        int totalSize = parts.stream().mapToInt(b -> b.length).sum();
        byte[] result = new byte[totalSize];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }
}
