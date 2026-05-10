package com.abisupc.controller;

import com.abisupc.service.AdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;

public class AdminController {

    private static final AdminService service = new AdminService();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void login(Context ctx) {
        try {
            ObjectNode body = mapper.readValue(ctx.body(), ObjectNode.class);

            String usuario = body.has("usuario") ? body.get("usuario").asText() : null;
            String password = body.has("password") ? body.get("password").asText() : null;

            if (usuario == null || usuario.isBlank() || password == null || password.isBlank()) {
                ctx.status(400).json(errorJson("Usuario y contraseña son requeridos"));
                return;
            }

            AdminService.LoginResult result = service.login(usuario, password);

            if (result.success) {
                ObjectNode response = mapper.createObjectNode();
                response.put("success", true);
                response.put("token", result.token);
                response.put("message", result.message);
                ctx.json(response.toString());
            } else {
                ctx.status(401).json(errorJson(result.message));
            }

        } catch (Exception e) {
            System.err.println("[AdminController] Error login: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).json(errorJson("Error interno del servidor: " + e.getMessage()));
        }
    }

    public static void logout(Context ctx) {
        try {
            String token = ctx.header("Authorization");

            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            if (token == null || token.isBlank()) {
                // try body
                try {
                    ObjectNode body = mapper.readValue(ctx.body(), ObjectNode.class);
                    token = body.has("token") ? body.get("token").asText() : null;
                } catch (Exception ignored) {
                }
            }

            if (token == null || token.isBlank()) {
                ctx.status(400).json(errorJson("Token requerido para cerrar sesion"));
                return;
            }

            boolean ok = service.logout(token);
            ObjectNode response = mapper.createObjectNode();
            response.put("success", ok);
            response.put("message", ok ? "Sesion cerrada exitosamente" : "Token invalido o sesion ya finalizada");
            ctx.json(response.toString());

        } catch (Exception e) {
            System.err.println("[AdminController] Error logout: " + e.getMessage());
            ctx.status(500).json(errorJson("Error interno del servidor"));
        }
    }

    private static String errorJson(String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("success", false);
        node.put("message", message);
        return node.toString();
    }
}
