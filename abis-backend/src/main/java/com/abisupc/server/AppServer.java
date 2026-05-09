package com.abisupc.server;

import com.abisupc.controller.TestController;
import com.abisupc.controller.EnrollController;
import com.abisupc.controller.VerifyController;
import com.abisupc.controller.OcrController;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class AppServer {

    public static void main(String[] args) {

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/", Location.CLASSPATH);
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

        // Biometrico
        app.post("/api/enroll", EnrollController::enroll);
        app.post("/api/verify", VerifyController::verify);

        // OCR
        app.post("/api/document/scan", OcrController::scan);

        System.out.println("ABIS Backend en http://localhost:7000");
        System.out.println("  GET  /api/health");
        System.out.println("  GET  /api/status");
        System.out.println("  POST /api/enroll          (biometrico :8001)");
        System.out.println("  POST /api/verify          (biometrico :8001)");
        System.out.println("  POST /api/document/scan   (OCR :8002)");
    }
}
