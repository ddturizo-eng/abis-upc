package com.abisupc.repository;

import com.abisupc.model.Jurado;
import java.util.List;

public class JuradoRepository {
    public void save(Jurado jurado) {
        // Implementar en E1-A05: INSERT INTO JURADOS (MESA_JURADOS_IDMESA, VOTANTES_IDENTIFICACION, ...)
        // La PK es compuesta: ambas columnas juntas. No existe un id unico individual.
    }

    public List<Jurado> findByMesa(Long idMesa) {
        // Implementar en E1-A05: SELECT * FROM JURADOS WHERE MESA_JURADOS_IDMESA = ?
        return null;
    }

    public List<Jurado> findByIdentificacion(String identificacion) {
        // Implementar en E1-A05: SELECT * FROM JURADOS WHERE VOTANTES_IDENTIFICACION = ?
        return null;
    }

    public boolean esJurado(String identificacion) {
        // Implementar en E1-A05: SELECT COUNT(*) > 0 FROM JURADOS WHERE VOTANTES_IDENTIFICACION = ?
        // Verifica si una identificacion pertenece a algun jurado activo.
        return false;
    }

    public void asignarAMesa(String identificacion, Long idMesa, String cargo) {
        // Implementar en E1-A05: UPDATE JURADOS SET MESA_JURADOS_IDMESA = ?, CARGO = ?
        // WHERE VOTANTES_IDENTIFICACION = ?
    }
}