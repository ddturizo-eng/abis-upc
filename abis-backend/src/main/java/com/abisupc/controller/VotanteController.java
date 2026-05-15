package com.abisupc.controller;

import com.abisupc.model.EstadoVotante;
import com.abisupc.model.Votante;
import com.abisupc.repository.EleccionRepository;
import com.abisupc.repository.RegistroVotoRepository;
import com.abisupc.repository.VotanteRepository;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class VotanteController {

    private static final VotanteRepository repository = new VotanteRepository();
    private static final RegistroVotoRepository registroVotoRepository = new RegistroVotoRepository();
    private static final EleccionRepository eleccionRepository = new EleccionRepository();

    public static void getAll(Context ctx) {
        ctx.json(repository.findAll());
    }

    public static void segundaLlave(Context ctx) {
        try {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            String qrCedula = normalizarQrCedula((String) body.get("qr_cedula"));
            String identificacion = normalizarIdentificacion((String) body.get("identificacion"));

            if (qrCedula == null || qrCedula.isBlank()) {
                ctx.status(400).json(Map.of("error", "Código no reconocido"));
                return;
            }
            if (identificacion == null || identificacion.isBlank()) {
                ctx.status(400).json(Map.of("error", "La identificación no coincide"));
                return;
            }

            Optional<Votante> encontrado = repository.findByQrCedula(qrCedula);
            if (encontrado.isEmpty()) {
                ctx.status(404).json(Map.of("error", "Código no reconocido"));
                return;
            }

            Votante votante = encontrado.get();
            if (!identificacion.equals(votante.getIdentificacion())) {
                ctx.status(409).json(Map.of("error", "La identificación no coincide"));
                return;
            }

            if (!EstadoVotante.PENDIENTE.name().equals(votante.getEstadoVoto())) {
                ctx.status(409).json(Map.of("error", "Votante no habilitado"));
                return;
            }

            Long idEleccion = eleccionRepository.findActiva().map(e -> e.getId()).orElse(null);
            boolean yaVoto = idEleccion != null && registroVotoRepository.yaVoto(identificacion, idEleccion);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requiere_confirmacion_visual", true);
            response.put("ya_voto", yaVoto);
            response.put("id_eleccion", idEleccion);
            response.put("votante", datosJornada(votante));
            ctx.json(response);
        } catch (Exception e) {
            System.err.println("[VotanteController] segundaLlave error: " + e.getMessage());
            ctx.status(500).json(Map.of("error", "Código no reconocido"));
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

    private static String normalizarIdentificacion(String value) {
        return value == null ? null : value.replaceAll("\\D", "").trim();
    }

    private static Map<String, Object> datosJornada(Votante votante) {
        Map<String, Object> data = new HashMap<>();
        data.put("identificacion", votante.getIdentificacion());
        data.put("primer_nombre", votante.getPrimerNombre());
        data.put("segundo_nombre", votante.getSegundoNombre());
        data.put("primer_apellido", votante.getPrimerApellido());
        data.put("segundo_apellido", votante.getSegundoApellido());
        data.put("estado_voto", votante.getEstadoVoto());
        data.put("foto_url", votante.getFotoUrl());
        data.put("rol_id", votante.getIdRol());
        data.put("puesto_id", votante.getIdPuesto());
        return data;
    }
}
