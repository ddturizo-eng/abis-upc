package com.abisupc.controller;

import com.abisupc.service.OcrPythonService;
import io.javalin.http.Context;

import java.io.InputStream;

/**
 * Endpoint de escaneo OCR de documentos de identidad.
 *
 * <p>Recibe la imagen frontal y opcionalmente la trasera de una cedula
 * colombiana, la envia al microservicio Python de OCR y retorna los datos
 * extraidos (identificacion, nombres, apellidos, fecha de nacimiento).
 */
public class OcrController {

    private static final OcrPythonService ocrService = new OcrPythonService();

    /**
     * Escanea una cedula colombiana con OCR y extrae sus datos.
     *
     * <p>Recibe la imagen frontal (requerida) y trasera (opcional) de la cedula.
     * El parametro {@code doc_type} permite forzar el tipo de documento o usar
     * deteccion automatica.
     *
     * @param ctx contexto HTTP con multipart {@code front}, opcional {@code back} y {@code doc_type}
     */
    public static void scan(Context ctx) {
        try {
            var frontFile = ctx.uploadedFile("front");
            if (frontFile == null) {
                ctx.status(400).json("{\"error\":\"Archivo front requerido\"}");
                return;
            }
            byte[] frontBytes;
            try (InputStream input = frontFile.content()) {
                frontBytes = input.readAllBytes();
            }

            byte[] backBytes = null;
            var backFile = ctx.uploadedFile("back");
            if (backFile != null) {
                try (InputStream input = backFile.content()) {
                    backBytes = input.readAllBytes();
                }
            }

            String docType = ctx.formParam("doc_type");
            if (docType == null) {
                docType = "auto";
            }

            var result = ocrService.scan(frontBytes, backBytes, docType);
            ctx.json(result != null ? result : "{\"error\":\"OCR fallo\"}");

        } catch (Exception e) {
            ctx.status(500).json("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
