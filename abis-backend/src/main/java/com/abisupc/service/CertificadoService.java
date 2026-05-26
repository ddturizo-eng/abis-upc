package com.abisupc.service;

import com.abisupc.dto.CertificadoEnvioRequest;
import com.abisupc.dto.CertificadoEnvioResponse;
import com.abisupc.integration.CertificadoClient;
import com.abisupc.model.AuditoriaCorreo;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public Map<String, Object> reenviarCertificado(Long idAuditoria) throws IOException {
        if (idAuditoria == null || idAuditoria <= 0) {
            throw new IllegalArgumentException("idAuditoria requerido");
        }
        AuditoriaCorreo auditoria = auditoriaCorreoRepo.findById(idAuditoria)
                .orElseThrow(() -> new IllegalArgumentException("Auditoria de certificado no encontrada"));
        Long nuevoId = reenviarDesdeAuditoria(auditoria);
        return Map.of("idAuditoria", nuevoId);
    }

    public Map<String, Object> reenviarCertificado(String identificacion, Long idEleccion) throws IOException {
        validarEntrada(identificacion, idEleccion);
        AuditoriaCorreo auditoria = auditoriaCorreoRepo
                .findUltimaPorVotanteEleccionORegistro(identificacion, idEleccion)
                .orElseThrow(() -> new IllegalStateException("El votante no tiene registro de voto para la eleccion"));
        Long nuevoId = reenviarDesdeAuditoria(auditoria);
        return Map.of("idAuditoria", nuevoId);
    }

    public List<Map<String, Object>> listarCertificados(Long idEleccion, int limit) {
        List<Map<String, Object>> response = new ArrayList<>();
        for (AuditoriaCorreo auditoria : auditoriaCorreoRepo.findUltimasPorRegistrosVoto(idEleccion, limit)) {
            response.add(toResumen(auditoria));
        }
        return response;
    }

    public Map<String, Object> resumenPanel(Long idEleccion) {
        List<AuditoriaCorreo> auditorias = auditoriaCorreoRepo.findUltimasPorRegistrosVoto(idEleccion, 200);
        long enviados = auditorias.stream().filter(a -> "ENVIADO".equalsIgnoreCase(a.getEstado())).count();
        long errores = auditorias.stream().filter(a -> "ERROR".equalsIgnoreCase(a.getEstado())).count();
        long pendientes = auditorias.stream()
                .filter(a -> !"ENVIADO".equalsIgnoreCase(a.getEstado()) && !"ERROR".equalsIgnoreCase(a.getEstado()))
                .count();
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("total", auditorias.size());
        resumen.put("enviados", enviados);
        resumen.put("errores", errores);
        resumen.put("pendientes", pendientes);
        return resumen;
    }

    public void prepararAuditoria() {
        auditoriaCorreoRepo.asegurarInfraestructura();
    }

    private Long reenviarDesdeAuditoria(AuditoriaCorreo auditoria) throws IOException {
        String codigoCertificado = auditoria.getCodigoCertificado();
        if (codigoCertificado == null || codigoCertificado.isBlank()) {
            codigoCertificado = generarCodigoCertificado();
        }

        Votante votante = votanteRepo.findByIdentificacion(auditoria.getIdentificacion())
                .orElseThrow(() -> new IllegalArgumentException("Votante no encontrado: " + auditoria.getIdentificacion()));
        Eleccion eleccion = eleccionRepo.findById(auditoria.getIdEleccion())
                .orElseThrow(() -> new IllegalArgumentException("Eleccion no encontrada: " + auditoria.getIdEleccion()));
        RegistroVoto registro = registroVotoRepo.findByIdentificacionEleccion(auditoria.getIdentificacion(), auditoria.getIdEleccion())
                .orElseThrow(() -> new IllegalStateException("El votante no tiene registro de voto para la eleccion"));
        PuestoVotacion puesto = puestoRepo.findById(registro.getIdPuesto())
                .orElseThrow(() -> new IllegalStateException("Puesto de votacion no encontrado para el registro"));

        Long idReintento = auditoriaCorreoRepo.registrarReintento(
                auditoria.getIdentificacion(),
                auditoria.getIdEleccion(),
                codigoCertificado
        );
        try {
            CertificadoEnvioResponse response = certificadoClient.enviar(
                    construirPayload(votante, eleccion, registro, puesto, codigoCertificado)
            );
            auditoriaCorreoRepo.marcarEnviado(idReintento, response.getMessageId());
            return idReintento;
        } catch (Exception e) {
            auditoriaCorreoRepo.marcarError(idReintento, e.getMessage());
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("No fue posible reenviar certificado", e);
        }
    }

    private Map<String, Object> toResumen(AuditoriaCorreo auditoria) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("idAuditoria", auditoria.getId());
        item.put("identificacion", auditoria.getIdentificacion());
        item.put("idEleccion", auditoria.getIdEleccion());
        item.put("correo", auditoria.getCorreoVotante());
        item.put("estado", auditoria.getEstado());
        item.put("provider", auditoria.getProvider());
        item.put("messageId", auditoria.getMessageId());
        item.put("codigoCertificado", auditoria.getCodigoCertificado());
        item.put("observaciones", auditoria.getObservaciones());
        item.put("fechaSolicitud", auditoria.getFechaSolicitud() != null ? auditoria.getFechaSolicitud().toInstant().toString() : null);
        item.put("fechaEnvio", auditoria.getFechaEnvio() != null ? auditoria.getFechaEnvio().toInstant().toString() : null);
        item.put("nombre", auditoria.getNombreCompleto() != null ? auditoria.getNombreCompleto() : "");
        item.put("eleccion", auditoria.getNombreEleccion() != null ? auditoria.getNombreEleccion() : "");
        return item;
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
