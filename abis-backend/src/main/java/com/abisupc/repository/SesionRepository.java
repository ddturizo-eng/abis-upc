package com.abisupc.repository;

import com.abisupc.model.Sesion;
import java.util.List;

public class SesionRepository implements Repository<Sesion> {
    @Override
    public Sesion findById(Long id) {
        return null;
    }

    @Override
    public List<Sesion> findAll() {
        return null;
    }

    @Override
    public void save(Sesion entity) {
    }

    @Override
    public void update(Sesion entity) {
    }

    @Override
    public void delete(Long id) {
    }

    public Sesion findByToken(String token) {
        // Implementar en E1-A05: SELECT * FROM SESIONES WHERE TOKEN = ?
        return null;
    }

    public Sesion findActivaByAdmin(Long idAdmin) {
        // Implementar en E1-A05: SELECT * FROM SESIONES
        // WHERE ADMINISTRADORES_IDADMIN = ? AND FECHA_FIN IS NULL
        // Null en fechaFin indica sesion activa (D6).
        return null;
    }

    public void invalidarToken(String token) {
        // Implementar en E1-A05: UPDATE SESIONES SET FECHA_FIN = NOW()
        // WHERE TOKEN = ? AND FECHA_FIN IS NULL
    }

    public void limpiarSesionesExpiradas() {
        // Implementar en E1-A05: DELETE FROM SESIONES WHERE FECHA_FIN IS NOT NULL
        // AND FECHA_FIN < NOW() - INTERVAL '30 DIAS'
        // Limpieza periodica de sesiones cerradas antiguas.
    }
}