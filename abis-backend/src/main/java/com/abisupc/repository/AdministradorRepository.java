package com.abisupc.repository;

import com.abisupc.model.Administrador;
import java.util.List;

public class AdministradorRepository implements Repository<Administrador> {
    @Override
    public Administrador findById(Long id) {

        return null;
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

    public Administrador findByUsuario(String usuario) {
        // Implementar en E1-A05: SELECT * FROM ADMINISTRADORES WHERE USUARIO = ?
        return null;
    }

    public Administrador findByCorreo(String correo) {
        // Implementar en E1-A05: SELECT * FROM ADMINISTRADORES WHERE CORREO = ?
        return null;
    }
}