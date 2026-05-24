package com.abisupc.controller;

import com.abisupc.config.AppConfig;
import com.abisupc.integration.BiometricClient;
import com.abisupc.integration.CertificadoClient;
import io.javalin.Javalin;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ABIS-UPC | Capa de Controladores
 * Health check y status de todos los servicios del sistema.
 */
public class TestController {

    public static void register(Javalin app) {

        app.get("/api/health", ctx -> {
            boolean pythonAlive = BiometricClient.isAlive();
            boolean ocrAlive = BiometricClient.isOcrAlive();
            boolean databaseAlive = isDatabaseAlive();
            boolean nativeAlive = isPortOpen("localhost", 8765);
            boolean emailAlive = new CertificadoClient().isAlive();

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("java", "ok");
            status.put("backend", "ok");
            status.put("biometric", pythonAlive ? "ok" : "offline");
            status.put("ocr", ocrAlive ? "ok" : "offline");
            status.put("native", nativeAlive ? "ok" : "offline");
            status.put("database", databaseAlive ? "ok" : "offline");
            status.put("email", emailAlive ? "ok" : "offline");
            status.put("mensaje", (pythonAlive && ocrAlive && nativeAlive && databaseAlive && emailAlive)
                    ? "Todos los sistemas operativos"
                    : "Verificar servicios externos y Oracle XE");

            ctx.json(status);
        });
    }

    private static boolean isDatabaseAlive() {
        try (Connection ignored = AppConfig.getConnection()) {
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
