package com.abisupc.repository;

import com.abisupc.model.Administrador;
import java.util.List;
import java.util.Optional;

public class AdministradorRepository implements Repository<Administrador> {
    @Override
    public Optional<Administrador> findById(Long id) {

        return Optional.empty();
    }

    @Override
    public List<Administrador> findAll() {

        return null;
    }

    @Override
    public void save(Administrador entity) {
    }

    @Override
    public void update(Administrador entity) {
    }

    @Override
    public void delete(Long id) {
    }

    public Optional<Administrador> findByUsuario(String usuario) {
        // Implementar en E1-A05: SELECT * FROM ADMINISTRADORES WHERE USUARIO = ?
        return Optional.empty();
    }

    public Optional<Administrador> findByCorreo(String correo) {
        // Implementar en E1-A05: SELECT * FROM ADMINISTRADORES WHERE CORREO = ?
        return Optional.empty();
    }
}