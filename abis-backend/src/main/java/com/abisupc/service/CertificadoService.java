package com.abisupc.service;

import com.abisupc.dto.CertificadoEnvioRequest;
import com.abisupc.dto.CertificadoEnvioResponse;
import com.abisupc.integration.CertificadoClient;
import com.abisupc.model.Eleccion;
import com.abisupc.model.PuestoVotacion;
import com.abisupc.model.RegistroVoto;
import com.abisupc.model.Votante;
import com.abisupc.repository.AuditoriaCorreoRepository;
import com.abisupc.repository.EleccionRepository;
import com.abisupc.repository.PuestoVotacionRepository;
import com.abisupc.repository.RegistroVotoRepository;
import com.abisupc.repository.VotanteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Orquesta el envio post-voto del certificado sin afectar la transaccion electoral.
 */
public class CertificadoService {

    private static final DateTimeFormatter FECHA_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AuditoriaCorreoRepository auditoriaCorreoRepo;
    private final VotanteRepository votanteRepo;
    private final EleccionRepository eleccionRepo;
    private final RegistroVotoRepository registroVotoRepo;
    private final PuestoVotacionRepository puestoRepo;
    private final CertificadoClient certificadoClient;

    public CertificadoService() {
        this(
                new AuditoriaCorreoRepository(),
                new VotanteRepository(),
                new EleccionRepository(),
                new RegistroVotoRepository(),
                new PuestoVotacionRepository(),
                new CertificadoClient()
        );
    }

    public CertificadoService(
            AuditoriaCorreoRepository auditoriaCorreoRepo,
            VotanteRepository votanteRepo,
            EleccionRepository eleccionRepo,
            RegistroVotoRepository registroVotoRepo,
            PuestoVotacionRepository puestoRepo,
            CertificadoClient certificadoClient
    ) {
        this.auditoriaCorreoRepo = auditoriaCorreoRepo;
        this.votanteRepo = votanteRepo;
        this.eleccionRepo = eleccionRepo;
        this.registroVotoRepo = registroVotoRepo;
        this.puestoRepo = puestoRepo;
        this.certificadoClient = certificadoClient;
    }

    public void enviarCertificadoPostVoto(String identificacion, Long idEleccion) {
        try {
            enviarCertificado(identificacion, idEleccion);
        } catch (Exception e) {
            System.err.println("[CertificadoService] Certificado post-voto no bloqueante: " + e.getMessage());
        }
    }

    public void enviarCertificado(String identificacion, Long idEleccion) throws IOException {
        validarEntrada(identificacion, idEleccion);

        Votante votante = votanteRepo.findByIdentificacion(identificacion)
                .orElseThrow(() -> new IllegalArgumentException("Votante no encontrado: " + identificacion));
        Eleccion eleccion = eleccionRepo.findById(idEleccion)
                .orElseThrow(() -> new IllegalArgumentException("Eleccion no encontrada: " + idEleccion));
        RegistroVoto registro = registroVotoRepo.findByIdentificacionEleccion(identificacion, idEleccion)
                .orElseThrow(() -> new IllegalStateException("El votante no tiene registro de voto para la eleccion"));
        PuestoVotacion puesto = puestoRepo.findById(registro.getIdPuesto())
                .orElseThrow(() -> new IllegalStateException("Puesto de votacion no encontrado para el registro"));

        String codigoCertificado = generarCodigoCertificado();
        Long idAuditoria = auditoriaCorreoRepo.registrarSolicitud(identificacion, idEleccion, codigoCertificado);

        try {
            CertificadoEnvioResponse response = certificadoClient.enviar(
                    construirPayload(votante, eleccion, registro, puesto, codigoCertificado)
            );
            auditoriaCorreoRepo.marcarEnviado(idAuditoria, response.getMessageId());
        } catch (Exception e) {
            auditoriaCorreoRepo.marcarError(idAuditoria, e.getMessage());
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("No fue posible enviar certificado", e);
        }
    }

    private CertificadoEnvioRequest construirPayload(
            Votante votante,
            Eleccion eleccion,
            RegistroVoto registro,
            PuestoVotacion puesto,
            String codigoCertificado
    ) {
        CertificadoEnvioRequest payload = new CertificadoEnvioRequest();
        payload.setIdentificacion(votante.getIdentificacion());
        payload.setNombre(nombreCompleto(votante));
        payload.setCorreo(votante.getCorreo());
        payload.setIdEleccion(eleccion.getId());
        payload.setNombreEleccion(eleccion.getNombre());
        payload.setFechaVoto(fechaVoto(registro));
        payload.setCodigoCertificado(codigoCertificado);
        payload.setNombrePuesto(puesto.getNombrePuesto());
        payload.setSede(puesto.getSede());
        payload.setCiudad(puesto.getCiudad());
        return payload;
    }

    private String nombreCompleto(Votante votante) {
        return String.join(" ",
                safe(votante.getPrimerNombre()),
                safe(votante.getSegundoNombre()),
                safe(votante.getPrimerApellido()),
                safe(votante.getSegundoApellido())
        ).replaceAll("\\s+", " ").trim();
    }

    private String fechaVoto(RegistroVoto registro) {
        LocalDateTime fecha = registro.getFechaHora() != null ? registro.getFechaHora() : LocalDateTime.now();
        return FECHA_FORMATTER.format(fecha);
    }

    private String generarCodigoCertificado() {
        return "ABIS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private void validarEntrada(String identificacion, Long idEleccion) {
        if (identificacion == null || identificacion.isBlank()) {
            throw new IllegalArgumentException("identificacion requerida");
        }
        if (idEleccion == null || idEleccion <= 0) {
            throw new IllegalArgumentException("idEleccion requerido");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
