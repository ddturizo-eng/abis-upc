package com.abisupc.controller;

import com.abisupc.model.EstadoVotante;
import com.abisupc.model.RegistroRequest;
import com.abisupc.model.Votante;
import com.abisupc.repository.VotanteRepository;
import com.abisupc.util.OracleErrorHandler;
import io.javalin.http.Context;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.Map;

/**
 * Endpoint de registro inicial de votantes.
 *
 * <p>Valida los datos del votante, verifica duplicados por identificacion,
 * correo y QR de cedula, y persiste el nuevo registro con estado PENDIENTE.
 * Normaliza el QR de la cedula eliminando caracteres invisibles y saltos de linea.
 */
public class RegistroController {

    private static final VotanteRepository repository = new VotanteRepository();

    /**
     * Registra un nuevo votante en el censo electoral.
     *
     * <p>Valida campos requeridos, verifica que no exista por identificacion,
     * correo o QR de cedula, y persiste con estado PENDIENTE.
     *
     * @param ctx contexto HTTP con body {@link RegistroRequest}
     */
    public static void crear(Context ctx) {
        try {
            var body = ctx.bodyAsClass(RegistroRequest.class);

            if (body.identificacion == null || body.identificacion.isBlank()) {
                ctx.status(400).json("{\"error\":\"identificacion requerida\"}");
                return;
            }
            if (body.primerNombre == null || body.primerNombre.isBlank()) {
                ctx.status(400).json("{\"error\":\"primer_nombre requerido\"}");
                return;
            }
            if (body.primerApellido == null || body.primerApellido.isBlank()) {
                ctx.status(400).json("{\"error\":\"primer_apellido requerido\"}");
                return;
            }
            if (body.correo == null || body.correo.isBlank()) {
                ctx.status(400).json("{\"error\":\"correo requerido\"}");
                return;
            }
            if (body.idRol == null) {
                ctx.status(400).json("{\"error\":\"id_rol requerido\"}");
                return;
            }
            if (body.idPuesto == null) {
                ctx.status(400).json("{\"error\":\"id_puesto requerido\"}");
                return;
            }
            body.qrCedula = normalizarQrCedula(body.qrCedula);

            var existente = repository.findByIdentificacion(body.identificacion);
            if (existente.isPresent()) {
                ctx.status(409).json("{\"error\":\"Votante ya registrado\"}");
                return;
            }

            var porCorreo = repository.findByCorreo(body.correo);
            if (porCorreo.isPresent()) {
                ctx.status(409).json("{\"error\":\"Correo ya registrado\"}");
                return;
            }
            if (body.qrCedula != null && !body.qrCedula.isBlank() && repository.findByQrCedula(body.qrCedula).isPresent()) {
                ctx.status(409).json("{\"error\":\"Código no reconocido\"}");
                return;
            }

            Votante votante = new Votante();
            votante.setIdentificacion(body.identificacion);
            votante.setPrimerNombre(body.primerNombre);
            votante.setSegundoNombre(body.segundoNombre);
            votante.setPrimerApellido(body.primerApellido);
            votante.setSegundoApellido(body.segundoApellido);
            votante.setCorreo(body.correo);
            votante.setEstadoVoto(EstadoVotante.PENDIENTE.name());
            votante.setFechaConsentimiento(new Timestamp(System.currentTimeMillis()));
            votante.setFechaNacimiento(parsearFecha(body.fechaNacimiento));
            votante.setIdRol(body.idRol);
            votante.setIdPuesto(body.idPuesto);
            votante.setQrCedula(body.qrCedula);

            repository.save(votante);

            String nombreCompleto = body.primerNombre + " " + body.primerApellido;

            ctx.status(201).json("{\"success\":true,\"identificacion\":\""
                    + body.identificacion + "\",\"nombre_completo\":\""
                    + nombreCompleto + "\",\"message\":\"Votante registrado exitosamente\"}");

            System.out.println("[RegistroController] Votante registrado: " + body.identificacion);

        } catch (RuntimeException e) {
            System.err.println("[RegistroController] Error: " + e.getMessage());
            if (OracleErrorHandler.from(e).map(error -> {
                ctx.status(error.statusCode()).json(Map.of("error", error.message(), "oraCode", error.oraCode()));
                return true;
            }).orElse(false)) {
                return;
            }
            if (e.getMessage() != null && e.getMessage().contains("identificaci")) {
                ctx.status(409).json("{\"error\":\"Votante ya registrado\"}");
            } else if (e.getMessage() != null && e.getMessage().contains("UNIQUE") ||
                       (e.getMessage() != null && e.getMessage().contains("CORREO"))) {
                ctx.status(409).json("{\"error\":\"Correo ya registrado\"}");
            } else {
                ctx.status(500).json(Map.of("error", "Error interno al registrar votante"));
            }
        } catch (Exception e) {
            System.err.println("[RegistroController] Error: " + e.getMessage());
            if (OracleErrorHandler.from(e).map(error -> {
                ctx.status(error.statusCode()).json(Map.of("error", error.message(), "oraCode", error.oraCode()));
                return true;
            }).orElse(false)) {
                return;
            }
            ctx.status(500).json(Map.of("error", "Error interno al registrar votante"));
        }
    }

    private static Date parsearFecha(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Date.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizarQrCedula(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("[^\\S\\n]+", " ")
                .trim();
        return normalized.substring(0, Math.min(normalized.length(), 500));
    }
}
