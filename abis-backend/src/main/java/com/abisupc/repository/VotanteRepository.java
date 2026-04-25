package com.abisupc.repository;

import com.abisupc.model.Votante;
import java.util.List;

public class VotanteRepository implements Repository<Votante> {
    @Override
    public Votante findById(Long id) {
        return null;
    }

    @Override
    public List<Votante> findAll() {
        return null;
    }

    @Override
    public void save(Votante entity) {
    }

    @Override
    public void update(Votante entity) {
    }

    @Override
    public void delete(Long id) {
    }

    public Votante findByIdentificacion(String identificacion) {
        // Implementar en E1-A05: SELECT * FROM VOTANTES WHERE IDENTIFICACION = ?
        // La identificacion es el PK natural en Oracle, no el id heredado de Entity.
        return null;
    }

    public List<Votante> findByIdRol(Long idRol) {
        // Implementar en E1-A05: SELECT * FROM VOTANTES WHERE ROLES_IDROL = ?
        return null;
    }

    public List<Votante> findByIdPuesto(Long idPuesto) {
        // Implementar en E1-A05: SELECT * FROM VOTANTES WHERE PUESTOS_VOTACION_IDPUESTOS = ?
        return null;
    }

    public boolean estaHabilitado(String identificacion) {
        // Implementar en E1-A05: Verificar que VOTANTES.ESTADO_VOTO = 'PENDIENTE'
        // para la identificacion dada. Se usa EstadoVotante.PENDIENTE, no string magico.
        return false;
    }

    public void actualizarEstado(String identificacion, String estado) {
        // Implementar en E1-A05: UPDATE VOTANTES SET ESTADO_VOTO = ? WHERE IDENTIFICACION = ?
    }

    public void actualizarPlantilla(String identificacion, String templateCifrado) {
        // Implementar en E1-A05: UPDATE VOTANTES SET PLANTILLA_BIOMETRICA = ? WHERE IDENTIFICACION = ?
    }

    public void actualizarFoto(String identificacion, String fotoUrl) {
        // Implementar en E1-A05: UPDATE VOTANTES SET FOTO_URL = ? WHERE IDENTIFICACION = ?
    }

    public void anonimizarDatosBiometricos(String identificacion) {
        // Implementar en E1-A06: SET PLANTILLA_BIOMETRICA = NULL, HASHINTEGRIDADBIOMETRICA = NULL
        // Este metodo se usa para GDPR: eliminacion de datos biometricos de un votante especifico.
    }
}