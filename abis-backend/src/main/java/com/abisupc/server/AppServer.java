package com.abisupc.server;

import com.abisupc.controller.TestController;
import com.abisupc.controller.EnrollController;
import com.abisupc.controller.VerifyController;
import com.abisupc.controller.OcrController;
import com.abisupc.controller.AdminController;
import com.abisupc.controller.PuestoController;
import com.abisupc.controller.RegistroController;
import com.abisupc.controller.FotoController;
import com.abisupc.security.AuthMiddleware;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class AppServer {

    public static void main(String[] args) {

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("C:/PROYECTOS P3/abis-upc/abis-backend/src/main/resources", Location.EXTERNAL);
            config.bundledPlugins.enableCors(cors ->
                cors.addRule(it -> it.anyHost())
            );
        }).start(7000);

        // Health check
        TestController.register(app);

        // Status
        app.get("/api/status", ctx ->
            ctx.json("{\"service\":\"javalin\",\"status\":\"ok\"}")
        );

        // Puestos de votacion
        app.get("/api/puestos", PuestoController::getAll);

        // Biometrico
        app.post("/api/enroll", EnrollController::enroll);
        app.post("/api/verify", VerifyController::verify);

        // OCR
        app.post("/api/document/scan", OcrController::scan);

        // Registro de votantes
        app.post("/api/registro/preregistro", RegistroController::crear);

        // Foto del votante
        app.post("/api/votantes/foto", FotoController::subirFoto);

        // Auth (publica - login no requiere autenticacion previa)
        app.post("/api/auth/login", AdminController::login);
        app.post("/api/auth/logout", AdminController::logout);

        // Rutas de administracion protegidas por token de sesion
        AuthMiddleware auth = new AuthMiddleware();
        app.before("/api/admin/*", auth);

        System.out.println("ABIS Backend en http://localhost:7000");
        System.out.println("  GET  /api/health");
        System.out.println("  GET  /api/status");
        System.out.println("  POST /api/registro/preregistro  (Oracle)");
        System.out.println("  POST /api/votantes/foto   (foto rostro)");
        System.out.println("  POST /api/enroll          (biometrico :8001)");
        System.out.println("  POST /api/verify          (biometrico :8001)");
        System.out.println("  POST /api/document/scan   (OCR :8002)");
        System.out.println("  POST /api/auth/login      (admin)");
        System.out.println("  POST /api/auth/logout     (admin)");
        System.out.println("  /api/admin/*              (protegido por AuthMiddleware)");
    }
}
