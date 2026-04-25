package com.abisupc.repository;

import com.abisupc.model.PuestoVotacion;
import java.util.List;
import java.util.Optional;

public class PuestoVotacionRepository implements Repository<PuestoVotacion> {
    @Override
    public Optional<PuestoVotacion> findById(Long id) {
        return Optional.empty();
    }

    @Override
    public List<PuestoVotacion> findAll() {
        return null;
    }

    @Override
    public void save(PuestoVotacion entity) {
    }

    @Override
    public void update(PuestoVotacion entity) {
    }

    @Override
    public void delete(Long id) {
    }
}