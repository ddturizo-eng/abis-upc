package com.abisupc.controller;

import com.abisupc.repository.BiometriaOracleRepository;
import com.abisupc.util.OracleErrorHandler;
import io.javalin.http.Context;

import java.util.Base64;
import java.util.Map;

public class BiometriaController {

    private static final BiometriaOracleRepository biometriaRepo = new BiometriaOracleRepository();

    public static void enrolar(Context ctx) {
        try {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            String identificacion = text(body.get("identificacion"));
            String plantillaBase64 = text(body.get("plantilla"));
            String hash = text(body.get("hash"));

            if (identificacion == null || identificacion.isBlank()) {
                throw new IllegalArgumentException("identificacion requerida");
            }
            if (plantillaBase64 == null || plantillaBase64.isBlank()) {
                throw new IllegalArgumentException("plantilla requerida");
            }
            if (hash == null || hash.isBlank()) {
                throw new IllegalArgumentException("hash requerido");
            }

            byte[] plantilla = Base64.getDecoder().decode(plantillaBase64);
            biometriaRepo.enrolarBiometria(identificacion, plantilla, hash);
            ctx.status(201).json(Map.of("success", true, "message", "Biometria enrolada"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            if (handleOracle(ctx, e)) {
                return;
            }
            ctx.status(500).json(Map.of("error", "No fue posible enrolar la biometria"));
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static boolean handleOracle(Context ctx, Throwable e) {
        return OracleErrorHandler.from(e).map(error -> {
            ctx.status(error.statusCode()).json(Map.of("error", error.message(), "oraCode", error.oraCode()));
            return true;
        }).orElse(false);
    }
}
