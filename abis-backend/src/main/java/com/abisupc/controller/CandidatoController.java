package com.abisupc.controller;

import com.abisupc.dto.ApiResponse;
import com.abisupc.model.Candidato;
import com.abisupc.repository.CandidatoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

public class CandidatoController {

    private static final CandidatoRepository candidatoRepo = new CandidatoRepository();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String FOTOS_CANDIDATOS_DIR = "C:/PROYECTOS P3/abis-upc/abis-backend/src/main/resources/assets/fotos/candidatos/";

    public static void getByEleccion(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(ApiResponse.success(candidatoRepo.findByEleccion(id)));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void agregar(Context ctx) {
        try {
            Long idEleccion = Long.parseLong(ctx.pathParam("id"));
            boolean multipart = isMultipart(ctx);
            JsonNode body = multipart ? null : mapper.readTree(ctx.body());
            Candidato candidato = multipart ? parseCandidato(ctx) : parseCandidato(body);
            candidato.setFotoUrl(guardarFoto(multipart ? ctx.uploadedFile("foto") : null, null));
            Integer numeroCampania = multipart ? parseNumeroCampania(ctx) : parseNumeroCampania(body);
            String cargo = multipart ? text(ctx, "cargo") : text(body, "cargo");
            if (cargo == null || cargo.isBlank()) {
                throw new IllegalArgumentException("Cargo requerido");
            }

            Long idCandidato = candidatoRepo.savePersona(candidato);
            candidatoRepo.savePostulacion(idCandidato, idEleccion, numeroCampania, cargo.trim());
            ctx.status(201).json(ApiResponse.success("Candidato agregado"));
        } catch (IllegalArgumentException e) {
            ctx.status(409).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void editar(Context ctx) {
        try {
            Long idEleccion = Long.parseLong(ctx.pathParam("idEleccion"));
            Long idCandidato = Long.parseLong(ctx.pathParam("idCandidato"));
            boolean multipart = isMultipart(ctx);
            JsonNode body = multipart ? null : mapper.readTree(ctx.body());
            Candidato candidato = multipart ? parseCandidato(ctx) : parseCandidato(body);
            candidato.setId(idCandidato);
            candidato.setFotoUrl(guardarFoto(multipart ? ctx.uploadedFile("foto") : null, idCandidato));
            Integer numeroCampania = multipart ? parseNumeroCampania(ctx) : parseNumeroCampania(body);
            String cargo = multipart ? text(ctx, "cargo") : text(body, "cargo");
            if (cargo == null || cargo.isBlank()) {
                throw new IllegalArgumentException("Cargo requerido");
            }

            candidatoRepo.update(candidato);
            candidatoRepo.updatePostulacion(idCandidato, idEleccion, numeroCampania, cargo.trim());
            ctx.json(ApiResponse.success("Candidato actualizado"));
        } catch (IllegalArgumentException e) {
            ctx.status(409).json(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public static void eliminar(Context ctx) {
        try {
            Long idEleccion = Long.parseLong(ctx.pathParam("idEleccion"));
            Long idCandidato = Long.parseLong(ctx.pathParam("idCandidato"));
            if (candidatoRepo.tieneVotos(idCandidato, idEleccion)) {
                ctx.status(409).json(ApiResponse.error("No se puede eliminar: tiene votos asociados"));
                return;
            }

            candidatoRepo.deletePostulacion(idCandidato, idEleccion);
            ctx.json(ApiResponse.success("Candidato eliminado"));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    private static Candidato parseCandidato(JsonNode body) {
        String primerNombre = text(body, "primerNombre");
        String segundoNombre = text(body, "segundoNombre");
        String primerApellido = text(body, "primerApellido");
        String segundoApellido = text(body, "segundoApellido");

        if (primerNombre == null || primerNombre.isBlank()) {
            throw new IllegalArgumentException("Primer nombre requerido");
        }
        if (primerApellido == null || primerApellido.isBlank()) {
            throw new IllegalArgumentException("Primer apellido requerido");
        }

        Candidato candidato = new Candidato();
        candidato.setPrimerNombre(primerNombre.trim());
        candidato.setSegundoNombre(segundoNombre != null ? segundoNombre.trim() : null);
        candidato.setPrimerApellido(primerApellido.trim());
        candidato.setSegundoApellido(segundoApellido != null ? segundoApellido.trim() : null);
        String email = text(body, "email");
        if (email != null && !email.isBlank() && !email.contains("@")) {
            throw new IllegalArgumentException("Email invalido");
        }
        candidato.setEmail(email != null ? email.trim() : null);
        return candidato;
    }

    private static Candidato parseCandidato(Context ctx) {
        String primerNombre = text(ctx, "primerNombre");
        String segundoNombre = text(ctx, "segundoNombre");
        String primerApellido = text(ctx, "primerApellido");
        String segundoApellido = text(ctx, "segundoApellido");

        if (primerNombre == null || primerNombre.isBlank()) {
            throw new IllegalArgumentException("Primer nombre requerido");
        }
        if (primerApellido == null || primerApellido.isBlank()) {
            throw new IllegalArgumentException("Primer apellido requerido");
        }

        Candidato candidato = new Candidato();
        candidato.setPrimerNombre(primerNombre.trim());
        candidato.setSegundoNombre(segundoNombre != null ? segundoNombre.trim() : null);
        candidato.setPrimerApellido(primerApellido.trim());
        candidato.setSegundoApellido(segundoApellido != null ? segundoApellido.trim() : null);
        String email = text(ctx, "email");
        if (email != null && !email.isBlank() && !email.contains("@")) {
            throw new IllegalArgumentException("Email invalido");
        }
        candidato.setEmail(email != null ? email.trim() : null);
        return candidato;
    }

    private static Integer parseNumeroCampania(JsonNode body) {
        String numeroCampania = text(body, "numeroCampania");
        int numero;
        try {
            numero = Integer.parseInt(numeroCampania);
        } catch (Exception e) {
            throw new IllegalArgumentException("Número de campaña requerido");
        }
        if (numero <= 0) {
            throw new IllegalArgumentException("Número de campaña debe ser mayor que 0");
        }
        return numero;
    }

    private static Integer parseNumeroCampania(Context ctx) {
        String numeroCampania = text(ctx, "numeroCampania");
        int numero;
        try {
            numero = Integer.parseInt(numeroCampania);
        } catch (Exception e) {
            throw new IllegalArgumentException("NÃºmero de campaÃ±a requerido");
        }
        if (numero <= 0) {
            throw new IllegalArgumentException("NÃºmero de campaÃ±a debe ser mayor que 0");
        }
        return numero;
    }

    private static String text(JsonNode body, String field) {
        return body != null && body.hasNonNull(field) ? body.get(field).asText() : null;
    }

    private static String text(Context ctx, String field) {
        String value = ctx.formParam(field);
        return value != null ? value : null;
    }

    private static boolean isMultipart(Context ctx) {
        String contentType = ctx.contentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = ctx.header("Content-Type");
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = ctx.header("content-type");
        }
        if ((contentType == null || contentType.isBlank()) && ctx.req() != null) {
            contentType = ctx.req().getContentType();
        }
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data");
    }

    private static String guardarFoto(UploadedFile archivo, Long idCandidato) throws IOException {
        if (archivo == null) {
            return null;
        }

        Path dir = Paths.get(FOTOS_CANDIDATOS_DIR);
        Files.createDirectories(dir);

        String extension = extension(archivo.filename());
        String prefijo = idCandidato != null ? "candidato_" + idCandidato : "candidato";
        String nombreArchivo = prefijo + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
        Path destino = dir.resolve(nombreArchivo);
        try (InputStream input = archivo.content()) {
            Files.copy(input, destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        return "/assets/fotos/candidatos/" + nombreArchivo;
    }

    private static String extension(String filename) {
        if (filename == null) {
            return ".jpg";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        int index = lower.lastIndexOf('.');
        if (index < 0) {
            return ".jpg";
        }
        String extension = lower.substring(index);
        return switch (extension) {
            case ".jpg", ".jpeg", ".png", ".webp" -> extension;
            default -> ".jpg";
        };
    }
}
