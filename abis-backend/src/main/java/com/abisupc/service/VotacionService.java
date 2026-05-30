package com.abisupc.service;

import com.abisupc.repository.VotanteAdminRepository;
import com.abisupc.repository.VotoOracleRepository;
import com.abisupc.repository.TokenContingenciaRepository;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servicio central del flujo de votacion en el kiosco electoral.
 *
 * <p>Coordina la validacion de elegibilidad, el registro atomico del voto
 * y el envio asincronico del certificado de participacion. El certificado
 * se despacha en un hilo separado ({@code certificadoExecutor}) para no
 * agregar latencia al flujo critico de votacion.
 *
 * <p>Despues de registrar el voto, marca como usado el token de contingencia
 * si el votante lo habia utilizado para autenticarse, garantizando que el
 * token no pueda reutilizarse en una segunda votacion.
 */
public class VotacionService {

    private final VotoOracleRepository votoRepo;
    private final VotanteAdminRepository votanteAdminRepo;
    private final CertificadoService certificadoService;
    private final ExecutorService certificadoExecutor;
    private final TokenContingenciaRepository tokenContingenciaRepo;

    /** Constructor por defecto. Crea sus propios repositorios y executor. */
    public VotacionService() {
        this(new VotoOracleRepository(), new VotanteAdminRepository(), new CertificadoService(),
                Executors.newSingleThreadExecutor(), new TokenContingenciaRepository());
    }

    /**
     * Constructor para inyeccion de dependencias (util en pruebas).
     *
     * @param votoRepo             repositorio de votos Oracle
     * @param votanteAdminRepo     repositorio de operaciones sobre votantes
     * @param certificadoService   servicio de envio de certificados
     * @param certificadoExecutor  executor para envio asincronico
     * @param tokenContingenciaRepo repositorio de tokens de contingencia
     */
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

    /**
     * Verifica si un votante esta habilitado para votar en una eleccion especifica.
     *
     * @param identificacion cedula del votante
     * @param idEleccion     ID de la eleccion activa
     * @return mapa con {@code puede} ("S" o "N") y {@code motivo} si no puede votar
     * @throws SQLException             si falla el acceso a la base de datos
     * @throws IllegalArgumentException si los parametros son nulos o invalidos
     */
    public Map<String, String> votantePuedeVotar(String identificacion, Long idEleccion) throws SQLException {
        validarIdentificacion(identificacion);
        validarId(idEleccion, "idEleccion");
        return votanteAdminRepo.votantePuedeVotar(identificacion, idEleccion);
    }

    /**
     * Registra el voto de un votante de forma atomica en Oracle.
     *
     * <p>Verifica elegibilidad antes de registrar. Si el votante no puede votar,
     * lanza {@link IllegalStateException} con el motivo. Tras el registro exitoso,
     * marca como usado el token de contingencia (si aplica) y solicita el
     * certificado de forma asincronica para no bloquear la respuesta al kiosco.
     *
     * @param identificacion cedula del votante
     * @param idEleccion     ID de la eleccion activa
     * @param idCandidato    ID del candidato seleccionado
     * @param idPuesto       ID del puesto donde se emite el voto
     * @throws SQLException             si falla el stored procedure de registro
     * @throws IllegalStateException    si el votante no esta habilitado para votar
     * @throws IllegalArgumentException si los parametros son nulos o invalidos
     */
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

    /**
     * Solicita el envio del certificado en un hilo separado para no bloquear
     * la respuesta al kiosco. Los errores se registran pero no se propagan.
     *
     * @param identificacion cedula del votante
     * @param idEleccion     ID de la eleccion
     */
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
