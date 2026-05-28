package com.abisupc.service;

import com.abisupc.model.CandidatoEleccion;
import com.abisupc.repository.CandidatoRepository;
import com.abisupc.repository.VotoOracleRepository;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para calcular y consolidar los resultados de una eleccion.
 *
 * <p>Agrega los votos ponderados por candidato consultando {@link VotoOracleRepository}
 * e incluye el porcentaje de participacion de la jornada. Los votos ponderados
 * tienen en cuenta el {@code PESO_VOTO} del rol del votante congelado en el
 * momento del sufragio, garantizando que cambios posteriores al peso no alteren
 * los resultados historicos.
 */
public class ResultadosService {

    private final CandidatoRepository candidatoRepo;
    private final VotoOracleRepository votoRepo;

    /** Constructor por defecto. Crea sus propios repositorios. */
    public ResultadosService() {
        this(new CandidatoRepository(), new VotoOracleRepository());
    }

    /**
     * Constructor para inyeccion de dependencias (util en pruebas).
     *
     * @param candidatoRepo repositorio de candidatos
     * @param votoRepo      repositorio de votos Oracle
     */
    public ResultadosService(CandidatoRepository candidatoRepo, VotoOracleRepository votoRepo) {
        this.candidatoRepo = candidatoRepo;
        this.votoRepo = votoRepo;
    }

    /**
     * Calcula los resultados completos de una eleccion.
     *
     * <p>Para cada candidato obtiene su total de votos ponderados. Incluye
     * el porcentaje de participacion calculado sobre el total de votantes
     * habilitados para esa eleccion.
     *
     * @param idEleccion ID de la eleccion
     * @return mapa con {@code idEleccion}, {@code porcentajeParticipacion}
     *         y lista de {@code candidatos} con sus votos ponderados
     * @throws SQLException             si falla el acceso a la base de datos
     * @throws IllegalArgumentException si {@code idEleccion} es nulo o invalido
     */
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

    /**
     * Construye el mapa de resultado de un candidato especifico.
     *
     * @param candidato  datos del candidato en la eleccion
     * @param idEleccion ID de la eleccion
     * @return mapa con datos del candidato y sus votos ponderados
     * @throws RuntimeException si falla la consulta de votos
     */
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