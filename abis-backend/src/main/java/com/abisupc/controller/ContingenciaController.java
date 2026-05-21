package com.abisupc.controller;

import com.abisupc.service.ContingenciaTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ContingenciaController {

    private static final Set<WsContext> clients = ConcurrentHashMap.newKeySet();
    private static final ContingenciaTokenService service = new ContingenciaTokenService();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void register(Javalin app) {
        app.ws("/ws/contingencia-ui", ws -> {
            ws.onConnect(ctx -> {
                clients.add(ctx);
                System.out.println("[ContingenciaWS] Cliente conectado. Activos: " + clients.size());
            });
            ws.onClose(ctx -> {
                clients.remove(ctx);
                System.out.println("[ContingenciaWS] Cliente desconectado. Activos: " + clients.size());
            });
            ws.onError(ctx -> {
                clients.remove(ctx);
                System.err.println("[ContingenciaWS] Error: " + ctx.error().getMessage());
            });
        });
    }

    public static void generarToken(Context ctx) {
        try {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            String identificacion = text(body.get("identificacion"));
            Long idEleccion = number(body.get("idEleccion"));
            ctx.json(service.generarToken(identificacion, idEleccion));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("[ContingenciaController] generarToken: " + e.getMessage());
            ctx.status(500).json(Map.of("error", "No fue posible generar token de contingencia"));
        }
    }

    public static void scan(Context ctx) {
        try {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            String token = text(first(body, "token", "codigo", "raw"));
            String scannerId = text(first(body, "scannerId", "scanner_id"));
            Long idPuesto = number(first(body, "puestoId", "idPuesto", "id_puesto"));

            Map<String, Object> result = service.validarEscaneo(token, scannerId, idPuesto);
            broadcast(result);
            if (Boolean.TRUE.equals(result.get("success"))) {
                ctx.json(result);
                return;
            }
            ctx.status(statusFor(String.valueOf(result.get("type")))).json(result);
        } catch (Exception e) {
            Map<String, Object> result = Map.of(
                    "type", "SCAN_REJECTED",
                    "success", false,
                    "message", "No fue posible procesar escaneo de contingencia"
            );
            broadcast(result);
            System.err.println("[ContingenciaController] scan: " + e.getMessage());
            ctx.status(500).json(result);
        }
    }

    private static void broadcast(Map<String, Object> event) {
        clients.removeIf(client -> !client.session.isOpen());
        String json;
        try {
            json = mapper.writeValueAsString(event);
        } catch (Exception e) {
            System.err.println("[ContingenciaWS] No se pudo serializar evento: " + e.getMessage());
            return;
        }
        for (WsContext client : clients) {
            try {
                client.send(json);
            } catch (Exception e) {
                clients.remove(client);
                System.err.println("[ContingenciaWS] No se pudo enviar evento: " + e.getMessage());
            }
        }
    }

    private static int statusFor(String type) {
        return switch (type) {
            case "NOT_FOUND" -> 404;
            case "ALREADY_VOTED" -> 409;
            default -> 400;
        };
    }

    private static Object first(Map<?, ?> body, String... keys) {
        if (body == null) {
            return null;
        }
        for (String key : keys) {
            if (body.containsKey(key)) {
                return body.get(key);
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static Long number(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
