package com.abisupc.repository;

import com.abisupc.model.RegistroVoto;
import java.util.List;

public class RegistroVotoRepository implements Repository<RegistroVoto> {
    @Override
    public RegistroVoto findById(Long id) {
        return null;
    }

    @Override
    public List<RegistroVoto> findAll() {
        return null;
    }

    @Override
    public void save(RegistroVoto entity) {
    }

    @Override
    public void update(RegistroVoto entity) {
    }

    @Override
    public void delete(Long id) {
    }

    public boolean yaVoto(String identificacion, Long idEleccion) {
        // Implementar en E1-A05: SELECT COUNT(*) > 0 FROM REGISTRO_VOTOS
        // WHERE VOTANTES_IDENTIFICACION = ? AND ELECCIONES_IDELECCION = ?
        // Verifica si un votante especifico ya emitio su voto en una eleccion especifica.
        return false;
    }

    public List<RegistroVoto> findByEleccion(Long idEleccion) {
        // Implementar en E1-A05: SELECT * FROM REGISTRO_VOTOS WHERE ELECCIONES_IDELECCION = ?
        return null;
    }

    public List<RegistroVoto> findByIdentificacion(String identificacion) {
        // Implementar en E1-A05: SELECT * FROM REGISTRO_VOTOS WHERE VOTANTES_IDENTIFICACION = ?
        return null;
    }

    public int countByPuesto(Long idPuesto, Long idEleccion) {
        // Implementar en E1-A05: SELECT COUNT(*) FROM REGISTRO_VOTOS
        // WHERE PUESTOS_VOTACION_IDPUESTOS = ? AND ELECCIONES_IDELECCION = ?
        return 0;
    }
}