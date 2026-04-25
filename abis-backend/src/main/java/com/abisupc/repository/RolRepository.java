package com.abisupc.repository;

import com.abisupc.model.Rol;
import java.util.List;
import java.util.Optional;

public class RolRepository implements Repository<Rol> {
    @Override
    public Optional<Rol> findById(Long id) {
        return Optional.empty();
    }

    @Override
    public List<Rol> findAll() {
        return null;
    }

    @Override
    public void save(Rol entity) {
    }

    @Override
    public void update(Rol entity) {
    }

    @Override
    public void delete(Long id) {
    }

    public Optional<Rol> findByNombre(String nombre) {
        // Implementar en E1-A05: SELECT * FROM ROLES WHERE NOMBRE = ?
        return Optional.empty();
    }

    public boolean estaEnUso(Long idRol) {
        // Implementar en E1-A05: Verificar si existe al menos un Votante con este ROLES_IDROL.
        // Se usa COUNT(*) > 0 para determinar si el rol tiene votos asociados.
        return false;
    }

    public double getPesoVoto(Long idRol) {
        // Implementar en E1-A05: SELECT PESO_VOTO FROM ROLES WHERE ID_ROL = ?
        // Este metodo congela el peso del rol para un votante especifico en el momento del voto.
        return 0.0;
    }
}