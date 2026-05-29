package com.abisupc.service;

import com.abisupc.model.CandidatoEleccion;
import com.abisupc.repository.CandidatoRepository;
import com.abisupc.repository.VotoOracleRepository;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResultadosService {

    private final CandidatoRepository candidatoRepo;
    private final VotoOracleRepository votoRepo;

    public ResultadosService() {
        this(new CandidatoRepository(), new VotoOracleRepository());
    }

    public ResultadosService(CandidatoRepository candidatoRepo, VotoOracleRepository votoRepo) {
        this.candidatoRepo = candidatoRepo;
        this.votoRepo = votoRepo;
    }

    public Map<String, Object> resultadosEleccion(Long idEleccion) throws SQLException {
        if (idEleccion == null || idEleccion <= 0) {
            throw new IllegalArgumentException("idEleccion requerido");
        }

        List<Map<String, Object>> candidatos = candidatoRepo.findByEleccion(idEleccion).stream()
                .map(candidato -> candidatoResultado(candidato, idEleccion))
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("idEleccion", idEleccion);
        response.put("porcentajeParticipacion", votoRepo.porcentajeParticipacion(idEleccion));
        response.put("candidatos", candidatos);
        return response;
    }

    private Map<String, Object> candidatoResultado(CandidatoEleccion candidato, Long idEleccion) {
        try {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("idCandidato", candidato.getIdCandidato());
            item.put("idEleccion", candidato.getIdEleccion());
            item.put("numeroCampania", candidato.getNumeroCampania());
            item.put("cargo", candidato.getCargo());
            item.put("nombre", nombre(candidato));
            item.put("votosPonderados", votoRepo.calcularVotosCandidato(candidato.getIdCandidato(), idEleccion));
            return item;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String nombre(CandidatoEleccion candidato) {
        return List.of(
                safe(candidato.getPrimerNombre()),
                safe(candidato.getSegundoNombre()),
                safe(candidato.getPrimerApellido()),
                safe(candidato.getSegundoApellido())
        ).stream().filter(value -> !value.isBlank()).reduce((a, b) -> a + " " + b).orElse("");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
