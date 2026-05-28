package com.abisupc.controller;

import com.abisupc.repository.VotanteRepository;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Endpoint para carga de foto de votante.
 *
 * <p>Recibe una imagen por multipart, la almacena en el directorio de assets
 * y actualiza la ruta en el registro del votante. Genera un nombre de archivo
 * unico con UUID para evitar colisiones.
 */
public class FotoController {

    private static final VotanteRepository repository = new VotanteRepository();
    private static final String FOTOS_DIR = "C:/PROYECTOS P3/abis-upc/abis-backend/src/main/resources/assets/fotos/";

    /**
     * Sube y almacena la foto de un votante.
     *
     * <p>Genera un nombre de archivo unico con UUID y actualiza la ruta en
     * el registro del votante.
     *
     * @param ctx contexto HTTP con multipart {@code identificacion} y {@code foto}
     */
    public static void subirFoto(Context ctx) {
        try {
            String identificacion = ctx.formParam("identificacion");
            if (identificacion == null || identificacion.isBlank()) {
                ctx.status(400).json("{\"error\":\"identificacion requerida\"}");
                return;
            }

            UploadedFile archivo = ctx.uploadedFile("foto");
            if (archivo == null) {
                ctx.status(400).json("{\"error\":\"foto requerida\"}");
                return;
            }

            Path dir = Paths.get(FOTOS_DIR);
            Files.createDirectories(dir);

            String nombreArchivo = identificacion + "_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
            Path destino = dir.resolve(nombreArchivo);
            try (InputStream input = archivo.content()) {
                Files.copy(input, destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            String fotoUrl = "/assets/fotos/" + nombreArchivo;

            repository.actualizarFoto(identificacion, fotoUrl);

            ctx.status(200).json("{\"success\":true,\"foto_url\":\"" + fotoUrl + "\"}");

        } catch (IOException e) {
            ctx.status(500).json("{\"error\":\"Error guardando foto: " + e.getMessage() + "\"}");
        } catch (RuntimeException e) {
            ctx.status(500).json("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
