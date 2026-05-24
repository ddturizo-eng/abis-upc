package com.abisupc.controller;

import com.abisupc.service.OcrPythonService;
import io.javalin.http.Context;

import java.io.InputStream;

public class OcrController {

    private static final OcrPythonService ocrService = new OcrPythonService();

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
