package com.abisupc.service;

import com.abisupc.repository.VotanteAdminRepository;
import com.abisupc.repository.VotoOracleRepository;
import com.abisupc.repository.TokenContingenciaRepository;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VotacionService {

    private final VotoOracleRepository votoRepo;
    private final VotanteAdminRepository votanteAdminRepo;
    private final CertificadoService certificadoService;
    private final ExecutorService certificadoExecutor;
    private final TokenContingenciaRepository tokenContingenciaRepo;

    public VotacionService() {
        this(new VotoOracleRepository(), new VotanteAdminRepository(), new CertificadoService(), Executors.newSingleThreadExecutor(),
                new TokenContingenciaRepository());
    }

    public VotacionService(
            VotoOracleRepository votoRepo,
            VotanteAdminRepository votanteAdminRepo,
            CertificadoService certificadoService,
            ExecutorService certificadoExecutor,
            TokenContingenciaRepository tokenContingenciaRepo
    ) {
        this.votoRepo = votoRepo;
        this.votanteAdminRepo = votanteAdminRepo;
        this.certificadoService = certificadoService;
        this.certificadoExecutor = certificadoExecutor;
        this.tokenContingenciaRepo = tokenContingenciaRepo;
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
        tokenContingenciaRepo.marcarUsado(identificacion, idEleccion, idPuesto, "REGISTRO_VOTO");
        solicitarCertificadoAsync(identificacion, idEleccion);
    }

    private void solicitarCertificadoAsync(String identificacion, Long idEleccion) {
        certificadoExecutor.submit(() -> certificadoService.enviarCertificadoPostVoto(identificacion, idEleccion));
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
