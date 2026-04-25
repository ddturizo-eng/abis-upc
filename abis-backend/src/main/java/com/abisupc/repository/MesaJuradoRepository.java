package com.abisupc.repository;

import com.abisupc.model.MesaJurado;
import java.util.List;

public class MesaJuradoRepository implements Repository<MesaJurado> {
    @Override
    public MesaJurado findById(Long id) {
        return null;
    }

    @Override
    public List<MesaJurado> findAll() {
        return null;
    }

    @Override
    public void save(MesaJurado entity) {
    }

    @Override
    public void update(MesaJurado entity) {
    }

    @Override
    public void delete(Long id) {
    }

    public List<MesaJurado> findByPuesto(Long idPuesto) {
        // Implementar en E1-A05: SELECT * FROM MESA_JURADOS WHERE PUESTOS_VOTACION_IDPUESTOS = ?
        return null;
    }

    public List<MesaJurado> findActivas() {
        // Implementar en E1-A05: Mesas donde HORA_SALIDA es NULL (aun no han cerrado).
        return null;
    }
}