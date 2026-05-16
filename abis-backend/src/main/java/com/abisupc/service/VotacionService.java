package com.abisupc.service;

import com.abisupc.repository.VotanteAdminRepository;
import com.abisupc.repository.VotoOracleRepository;

import java.sql.SQLException;
import java.util.Map;

public class VotacionService {

    private final VotoOracleRepository votoRepo;
    private final VotanteAdminRepository votanteAdminRepo;

    public VotacionService() {
        this(new VotoOracleRepository(), new VotanteAdminRepository());
    }

    public VotacionService(VotoOracleRepository votoRepo, VotanteAdminRepository votanteAdminRepo) {
        this.votoRepo = votoRepo;
        this.votanteAdminRepo = votanteAdminRepo;
    }

    public Map<String, String> votantePuedeVotar(String identificacion, Long idEleccion) throws SQLException {
        validarIdentificacion(identificacion);
        validarId(idEleccion, "idEleccion");
        return votanteAdminRepo.votantePuedeVotar(identificacion, idEleccion);
    }

    public void registrarVoto(String identificacion, Long idEleccion, Long idCandidato, Long idPuesto) throws SQLException {
        validarIdentificacion(identificacion);
        validarId(idEleccion, "idEleccion");
        validarId(idPuesto, "idPuesto");

        Map<String, String> validacion = votanteAdminRepo.votantePuedeVotar(identificacion, idEleccion);
        if (!"S".equalsIgnoreCase(validacion.get("puede"))) {
            throw new IllegalStateException(validacion.getOrDefault("motivo", "El votante no puede votar"));
        }

        votoRepo.registrarVoto(identificacion, idEleccion, idCandidato, idPuesto);
    }

    private void validarIdentificacion(String identificacion) {
        if (identificacion == null || identificacion.isBlank()) {
            throw new IllegalArgumentException("identificacion requerida");
        }
    }

    private void validarId(Long id, String field) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(field + " requerido");
        }
    }
}
