package com.abisupc.repository;

import com.abisupc.model.Eleccion;
import java.util.List;
import java.util.Optional;

public class EleccionRepository implements Repository<Eleccion> {
    @Override
    public Optional<Eleccion> findById(Long id) {
        return Optional.empty();
    }

    @Override
    public List<Eleccion> findAll() {
        return null;
    }

    @Override
    public void save(Eleccion entity) {
    }

    @Override
    public void update(Eleccion entity) {
    }

    @Override
    public void delete(Long id) {
    }

    public Optional<Eleccion> findActiva() {
        // Implementar en E1-A05: SELECT * FROM ELECCIONES WHERE ESTADO = 'EN_CURSO'
        // Una eleccion activa es aquella con estado EstadoEleccion.EN_CURSO.
        return Optional.empty();
    }

    public List<Eleccion> findByEstado(String estado) {
        // Implementar en E1-A05: SELECT * FROM ELECCIONES WHERE ESTADO = ?
        return null;
    }

    public void actualizarEstado(Long idEleccion, String estado) {
        // Implementar en E1-A05: UPDATE ELECCIONES SET ESTADO = ? WHERE ID_ELECCION = ?
    }
}