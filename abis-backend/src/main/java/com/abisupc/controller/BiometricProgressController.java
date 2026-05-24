package com.abisupc.controller;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.websocket.WsContext;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canal en vivo para replicar eventos del lector biometrico hacia la interfaz.
 */
public class BiometricProgressController {

    private static final Set<WsContext> clients = ConcurrentHashMap.newKeySet();

    public static void register(Javalin app) {
        app.ws("/ws/biometria-ui", ws -> {
            ws.onConnect(ctx -> {
                clients.add(ctx);
                System.out.println("[BiometricWS] Cliente conectado. Activos: " + clients.size());
            });
            ws.onClose(ctx -> {
                clients.remove(ctx);
                System.out.println("[BiometricWS] Cliente desconectado. Activos: " + clients.size());
            });
            ws.onError(ctx -> {
                clients.remove(ctx);
                System.err.println("[BiometricWS] Error: " + ctx.error().getMessage());
            });
        });
    }

    public static void reportProgress(Context ctx) {
        String json = ctx.body();
        broadcast(json);
        ctx.status(200).json("{\"success\":true}");
    }

    public static void broadcast(String json) {
        clients.removeIf(client -> !client.session.isOpen());
        for (WsContext client : clients) {
            try {
                client.send(json);
            } catch (Exception e) {
                clients.remove(client);
                System.err.println("[BiometricWS] No se pudo enviar progreso: " + e.getMessage());
            }
        }
    }
}
