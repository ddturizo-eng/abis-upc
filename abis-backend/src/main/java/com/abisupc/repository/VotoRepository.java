package com.abisupc.repository;

import com.abisupc.model.Voto;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VotoRepository implements Repository<Voto> {
    @Override
    public Optional<Voto> findById(Long id) {
        return Optional.empty();
    }

    @Override
    public List<Voto> findAll() {
        return null;
    }

    @Override
    public void save(Voto entity) {
    }

    @Override
    public void update(Voto entity) {
    }

    @Override
    public void delete(Long id) {
    }

    public List<Voto> findByEleccion(Long idEleccion) {
        // Implementar en E1-A05: SELECT * FROM VOTOS WHERE ELECCIONES_IDELECCION = ?
        return null;
    }

    public int countByCandidato(Long idCandidato) {
        // Implementar en E1-A05: SELECT COUNT(*) FROM VOTOS WHERE IDCANDIDATO = ?
        return 0;
    }

    public Map<Long, Integer> obtenerResultados(Long idEleccion) {
        // Implementar en E1-A05: SELECT IDCANDIDATO, COUNT(*) FROM VOTOS
        // WHERE ELECCIONES_IDELECCION = ? GROUP BY IDCANDIDATO
        // Mapa de idCandidato -> cantidad de votos (sin ponderacion).
        return null;
    }

    public Map<Long, Double> obtenerResultadosPonderados(Long idEleccion) {
        // Implementar en E1-A05: SELECT IDCANDIDATO, SUM(PESOVOTO_APLICADO) FROM VOTOS
        // WHERE ELECCIONES_IDELECCION = ? GROUP BY IDCANDIDATO
        // Mapa de idCandidato -> suma de pesoVotoAplicado (con ponderacion por rol).
        return null;
    }

    public Map<String, Integer> obtenerResultadosPorRol(Long idEleccion) {
        // Implementar en E1-A05: SELECT ROLES_IDROL, COUNT(*) FROM VOTOS
        // WHERE ELECCIONES_IDELECCION = ? GROUP BY ROLES_IDROL
        // Mapa de idRol -> cantidad de votos por rol (para auditoria).
        return null;
    }
}