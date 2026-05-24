package com.abisupc.model;

import java.sql.Timestamp;

/**
 * Registro operativo de los intentos de envio de certificados a votantes.
 */
public class AuditoriaCorreo extends Entity {

    private String identificacion;
    private Long idEleccion;
    private String correoVotante;
    private Timestamp fechaSolicitud;
    private Timestamp fechaEnvio;
    private String estado;
    private String provider;
    private String messageId;
    private String codigoCertificado;
    private String observaciones;

    public String getIdentificacion() {
        return identificacion;
    }

    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
    }

    public Long getIdEleccion() {
        return idEleccion;
    }

    public void setIdEleccion(Long idEleccion) {
        this.idEleccion = idEleccion;
    }

    public String getCorreoVotante() {
        return correoVotante;
    }

    public void setCorreoVotante(String correoVotante) {
        this.correoVotante = correoVotante;
    }

    public Timestamp getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(Timestamp fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    public Timestamp getFechaEnvio() {
        return fechaEnvio;
    }

    public void setFechaEnvio(Timestamp fechaEnvio) {
        this.fechaEnvio = fechaEnvio;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getCodigoCertificado() {
        return codigoCertificado;
    }

    public void setCodigoCertificado(String codigoCertificado) {
        this.codigoCertificado = codigoCertificado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
