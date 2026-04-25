package com.abisupc.repository;

import com.abisupc.model.Candidato;
import java.util.List;
import java.util.Optional;

public class CandidatoRepository implements Repository<Candidato> {
    @Override
    public Optional<Candidato> findById(Long id) {
        return Optional.empty();
    }

    @Override
    public List<Candidato> findAll() {
        return null;
    }

    @Override
    public void save(Candidato entity) {
    }

    @Override
    public void update(Candidato entity) {
    }

    @Override
    public void delete(Long id) {
    }

    public List<Candidato> findByEleccion(Long idEleccion) {
        // Implementar en E1-A05: SELECT * FROM CANDIDATOS WHERE ELECCIONES_IDELECCION = ?
        return null;
    }

    public List<Candidato> findByCargo(Long idEleccion, String cargo) {
        // Implementar en E1-A05: SELECT * FROM CANDIDATOS WHERE ELECCIONES_IDELELECCION = ? AND CARGO = ?
        return null;
    }

    public List<String> getCargosDistintos(Long idEleccion) {
        // Implementar en E1-A05: SELECT DISTINCT CARGO FROM CANDIDATOS WHERE ELECCIONES_IDELECCION = ?
        // Devuelve los cargos unicos para una eleccion especifica (ej: 'Alcalde', 'Gobernador').
        return null;
    }
}