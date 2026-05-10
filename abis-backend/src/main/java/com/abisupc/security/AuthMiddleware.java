package com.abisupc.security;

import com.abisupc.model.Sesion;
import com.abisupc.repository.SesionRepository;
import io.javalin.http.Context;
import io.javalin.http.Handler;

import java.util.Optional;

public class AuthMiddleware implements Handler {

    private final SesionRepository sesionRepo;

    public AuthMiddleware() {
        this.sesionRepo = new SesionRepository();
    }

    public AuthMiddleware(SesionRepository sesionRepo) {
        this.sesionRepo = sesionRepo;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        String authHeader = ctx.header("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ctx.status(401).json("{\"error\":\"Token de autenticacion requerido\"}");
            return;
        }

        String token = authHeader.substring(7);

        if (token.isBlank()) {
            ctx.status(401).json("{\"error\":\"Token de autenticacion requerido\"}");
            return;
        }

        Optional<Sesion> optSesion = sesionRepo.findByToken(token);

        if (optSesion.isEmpty()) {
            ctx.status(401).json("{\"error\":\"Token invalido o sesion expirada\"}");
            return;
        }

        Sesion sesion = optSesion.get();
        ctx.attribute("idAdmin", sesion.getIdAdministrador());
        ctx.attribute("token", sesion.getToken());
    }
}
