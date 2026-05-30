package com.abisupc.security;

import com.abisupc.model.Sesion;
import com.abisupc.repository.SesionRepository;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.UnauthorizedResponse;

import java.util.Optional;


/**
 * Middleware de autenticacion para rutas protegidas del backend.
 *
 * <p>Valida que cada peticion incluya un token de sesion activo en el
 * encabezado {@code Authorization: Bearer <token>}. Si el token existe
 * en {@code SESIONES} y {@code FECHA_FIN} es {@code null}, la peticion
 * se considera autenticada y se inyectan los atributos {@code idAdmin}
 * y {@code token} en el contexto para uso de los controllers.
 *
 * <p>Se registra en {@code AppServer} mediante {@code app.before(ruta, auth)}
 * sobre las rutas que requieren autenticacion (panel de administracion,
 * gestion de votantes, elecciones, jurados y contingencia).
 */
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
            throw new UnauthorizedResponse("Token de autenticacion requerido");
        }

        String token = authHeader.substring(7);

        if (token.isBlank()) {
            throw new UnauthorizedResponse("Token de autenticacion requerido");
        }

        Optional<Sesion> optSesion = sesionRepo.findByToken(token);

        if (optSesion.isEmpty()) {
            throw new UnauthorizedResponse("Token invalido o sesion expirada");
        }

        Sesion sesion = optSesion.get();
        ctx.attribute("idAdmin", sesion.getIdAdministrador());
        ctx.attribute("token", sesion.getToken());
    }
}