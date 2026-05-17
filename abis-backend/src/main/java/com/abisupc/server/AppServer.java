package com.abisupc.server;

import com.abisupc.controller.TestController;
import com.abisupc.controller.EnrollController;
import com.abisupc.controller.VerifyController;
import com.abisupc.controller.OcrController;
import com.abisupc.controller.AdminController;
import com.abisupc.controller.PuestoController;
import com.abisupc.controller.RegistroController;
import com.abisupc.controller.FotoController;
import com.abisupc.controller.EleccionController;
import com.abisupc.controller.CandidatoController;
import com.abisupc.controller.VotanteController;
import com.abisupc.controller.VotacionController;
import com.abisupc.controller.BiometricProgressController;
import com.abisupc.controller.JuradoController;
import com.abisupc.controller.BiometriaController;
import com.abisupc.security.AuthMiddleware;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public class AppServer {

    public static void main(String[] args) {

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("C:/PROYECTOS P3/abis-upc/abis-backend/src/main/resources", Location.EXTERNAL);
            config.bundledPlugins.enableCors(cors ->
                cors.addRule(it -> it.anyHost())
            );
        });

        app.before(ctx -> ctx.res().setCharacterEncoding(StandardCharsets.UTF_8.name()));
        app.after(ctx -> {
            String contentType = ctx.res().getContentType();
            if (ctx.path().endsWith(".html") || (contentType != null && contentType.startsWith("text/html"))) {
                ctx.res().setContentType("text/html; charset=UTF-8");
            }
        });

        // Health check
        TestController.register(app);
        BiometricProgressController.register(app);

        // Status
        app.get("/api/status", ctx ->
            ctx.json("{\"service\":\"javalin\",\"status\":\"ok\"}")
        );

        // Puestos de votacion
        app.get("/api/puestos", PuestoController::getAll);

        // Biometrico
        app.post("/api/enroll", EnrollController::enroll);
        app.get("/api/enroll/progress", EnrollController::progress);
        app.post("/api/interno/reportar-progreso", BiometricProgressController::reportProgress);
        app.post("/api/verify", VerifyController::verify);

        // OCR
        app.post("/api/document/scan", OcrController::scan);

        // Registro de votantes
        app.post("/api/registro/preregistro", RegistroController::crear);

        // Foto del votante
        app.post("/api/votantes/foto", FotoController::subirFoto);
        app.post("/api/votantes/segunda-llave", VotanteController::segundaLlave);
        app.get("/api/votantes/{id}/puede-votar", VotanteController::puedeVotar);
        app.get("/api/votacion/activa", VotacionController::activa);
        app.get("/api/votacion/votante", VotacionController::votante);
        app.post("/api/votacion/registrar", VotacionController::registrar);
        app.post("/api/votos/registrar", VotacionController::registrar);

        // Auth (publica - login no requiere autenticacion previa)
        app.post("/api/auth/login", AdminController::login);
        app.post("/api/auth/logout", AdminController::logout);

        // Elecciones y candidatos
        app.get("/api/elecciones", EleccionController::getAll);
        app.get("/api/elecciones/stats", EleccionController::stats);
        app.get("/api/elecciones/preparacion/{id}", EleccionController::preparacion);
        app.get("/api/elecciones/{id}/resultados", EleccionController::resultados);
        app.post("/api/elecciones", EleccionController::crear);
        app.put("/api/elecciones/{id}", EleccionController::editar);
        app.post("/api/elecciones/{id}/iniciar", EleccionController::iniciar);
        app.put("/api/elecciones/{id}/cerrar", EleccionController::cerrarAdmin);
        app.delete("/api/elecciones/{id}", EleccionController::eliminar);
        app.get("/api/elecciones/{id}/roles", EleccionController::getRoles);
        app.post("/api/elecciones/{id}/roles", EleccionController::configurarRol);
        app.get("/api/elecciones/{id}/candidatos", CandidatoController::getByEleccion);
        app.post("/api/elecciones/{id}/candidatos", CandidatoController::agregar);
        app.put("/api/elecciones/{idEleccion}/candidatos/{idCandidato}", CandidatoController::editar);
        app.delete("/api/elecciones/{idEleccion}/candidatos/{idCandidato}", CandidatoController::eliminar);

        // Mesas y jurados
        app.get("/api/jurados", JuradoController::getAll);
        app.post("/api/jurados/resumen-asignacion", JuradoController::resumenAsignacion);
        app.post("/api/jurados/pool-elegible", JuradoController::poolElegible);
        app.get("/api/jurados/mesas", JuradoController::mesas);
        app.get("/api/jurados/mesas/{id}", JuradoController::mesaDetalle);
        app.post("/api/jurados/mesas", JuradoController::crearMesa);
        app.put("/api/jurados/mesas/{id}", JuradoController::editarMesa);
        app.delete("/api/jurados/mesas/{id}", JuradoController::eliminarMesa);
        app.post("/api/jurados/asignar", JuradoController::asignar);
        app.post("/api/jurados/asignar-aleatorio", JuradoController::asignarAleatorio);
        app.delete("/api/jurados/{identificacion}/{idMesa}", JuradoController::remover);
        app.get("/api/jurados/exportar/pdf", JuradoController::exportarPdf);
        app.post("/api/biometria/enrolar", BiometriaController::enrolar);

        // Rutas de administracion protegidas por token de sesion
        AuthMiddleware auth = new AuthMiddleware();
        app.before("/api/admin/*", auth);
        app.before("/api/auditoria/*", auth);
        app.before("/api/jurados", auth);
        app.before("/api/jurados/*", auth);
        app.before("/api/biometria/enrolar", auth);
        app.before("/api/votantes", auth);
        app.before("/api/votantes/estadisticas", auth);
        app.before("/api/votantes/{id}/inhabilitar", auth);
        app.before("/api/votantes/{id}/habilitar", auth);
        app.before("/api/elecciones/{id}/cerrar", auth);
        app.get("/api/admin/dashboard", AdminController::dashboard);
        app.get("/api/auditoria/reciente", AdminController::auditoriaReciente);
        app.get("/api/votantes", VotanteController::getAll);
        app.get("/api/votantes/estadisticas", AdminController::estadisticasVotantes);
        app.put("/api/votantes/{id}/inhabilitar", VotanteController::inhabilitar);
        app.put("/api/votantes/{id}/habilitar", VotanteController::habilitar);

        app.start(7000);

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

        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            app.stop();
        }
    }
}
