package com.abisupc.security;

import com.abisupc.repository.SesionRepository;
import com.abisupc.model.Sesion;
import io.javalin.http.Context;
import java.util.Optional;

public class AuthMiddleware {

    private final SesionRepository sesionRepository;

    public AuthMiddleware(SesionRepository sesionRepository) {
        this.sesionRepository = sesionRepository;
    }

    public void authenticate(Context ctx) {
        String token = extraerToken(ctx);
        if (token == null) {
            rechazarAcceso(ctx, "Token no proporcionado");
            return;
        }

        Optional<Sesion> sesion = sesionRepository.findByToken(token);
        if (sesion.isEmpty() || sesion.get().getFechaFin() != null) {
            rechazarAcceso(ctx, "Token invalido o sesion expirada");
            return;
        }
    }

    public void requireAdmin(Context ctx) {
        authenticate(ctx);
        String token = extraerToken(ctx);
        if (token == null) return;

        Optional<Sesion> sesion = sesionRepository.findByToken(token);
        if (sesion.isEmpty()) return;

        // verificar rol SUPERADMIN si es necesario en el futuro
    }

    public String extraerToken(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring(7);
    }

    private void rechazarAcceso(Context ctx, String mensaje) {
        ctx.status(401).json("{\"error\": \"" + mensaje + "\"}");
    }
}