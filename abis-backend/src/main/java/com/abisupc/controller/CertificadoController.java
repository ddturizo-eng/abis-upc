package com.abisupc.controller;

import com.abisupc.dto.ApiResponse;
import com.abisupc.repository.EleccionRepository;
import com.abisupc.service.CertificadoService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoints de certificados y panel de notificaciones.
 *
 * <p>Permite listar certificados emitidos, ver el resumen del panel con
 * distribucion por eleccion, listar elecciones disponibles y reenviar
 * certificados fallidos por auditoria o por identificacion de votante.
 */
public class CertificadoController {

    private static final CertificadoService certificadoService = new CertificadoService();
    private static final EleccionRepository eleccionRepo = new EleccionRepository();

    public static void register(Javalin app) {
        app.get("/api/certificados", CertificadoController::listar);
        app.get("/api/certificados/resumen", CertificadoController::resumen);
        app.get("/api/certificados/elecciones", CertificadoController::elecciones);
        app.post("/api/certificados/{id}/reenviar", CertificadoController::reenviarPorAuditoria);
        app.post("/api/certificados/reenviar", CertificadoController::reenviarPorVotante);
    }

    /**
     * Lista certificados emitidos con limite y filtro por eleccion.
     *
     * @param ctx contexto HTTP con query params {@code eleccionId} y {@code limit}
     */
    private static void listar(Context ctx) {
        try {
            Long idEleccion = queryLong(ctx, "eleccionId");
            int limit = queryInt(ctx, "limit", 100);
            ctx.json(ApiResponse.success(certificadoService.listarCertificados(idEleccion, limit)));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Retorna el resumen del panel de certificados con distribucion por eleccion.
     *
     * @param ctx contexto HTTP con query param {@code eleccionId}
     */
    private static void resumen(Context ctx) {
        try {
            Long idEleccion = queryLong(ctx, "eleccionId");
            ctx.json(ApiResponse.success(certificadoService.resumenPanel(idEleccion)));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Lista las elecciones disponibles para filtrar certificados.
     *
     * @param ctx contexto HTTP
     */
    private static void elecciones(Context ctx) {
        try {
            var elecciones = eleccionRepo.findAll().stream().map(eleccion -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", eleccion.getId());
                item.put("nombre", eleccion.getNombre());
                item.put("estado", eleccion.getEstado() != null ? eleccion.getEstado().name() : null);
                return item;
            }).toList();
            ctx.json(ApiResponse.success(elecciones));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Reenvia un certificado fallido desde el panel de auditoria.
     *
     * @param ctx contexto HTTP con path param {@code id} del certificado
     */
    private static void reenviarPorAuditoria(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(ApiResponse.success(certificadoService.reenviarCertificado(id)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(502).json(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Reenvia un certificado buscando por identificacion de votante y eleccion.
     *
     * @param ctx contexto HTTP con body {@link ReenvioRequest}
     */
    private static void reenviarPorVotante(Context ctx) {
        try {
            ReenvioRequest request = ctx.bodyAsClass(ReenvioRequest.class);
            ctx.json(ApiResponse.success(certificadoService.reenviarCertificado(request.identificacion, request.idEleccion)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(502).json(ApiResponse.error(e.getMessage()));
        }
    }

    private static Long queryLong(Context ctx, String name) {
        String value = ctx.queryParam(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value);
    }

    private static int queryInt(Context ctx, String name, int defaultValue) {
        String value = ctx.queryParam(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    /** Request para reenvio de certificado por votante. */
    public static class ReenvioRequest {
        public String identificacion;
        public Long idEleccion;
    }
}
