package com.abisupc.model;

import java.util.Date;

/**
 * Datos biométricos separados de Votante para control de acceso diferenciado (Ley 1581).
 */
public class BiometriaVotante {

    private Long idBiometria;
    private String identificacion;
    private byte[] plantillaBiometrica;
    private String hashIntegridadBiometrica;
    private Date fechaEnrolamiento;
    private String activo;

    public Long getIdBiometria() {
        return idBiometria;
    }

    public void setIdBiometria(Long idBiometria) {
        this.idBiometria = idBiometria;
    }

    public String getIdentificacion() {
        return identificacion;
    }

    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
    }

    public byte[] getPlantillaBiometrica() {
        return plantillaBiometrica;
    }

    public void setPlantillaBiometrica(byte[] plantillaBiometrica) {
        this.plantillaBiometrica = plantillaBiometrica;
    }

    public String getHashIntegridadBiometrica() {
        return hashIntegridadBiometrica;
    }

    public void setHashIntegridadBiometrica(String hashIntegridadBiometrica) {
        this.hashIntegridadBiometrica = hashIntegridadBiometrica;
    }

    public Date getFechaEnrolamiento() {
        return fechaEnrolamiento;
    }

    public void setFechaEnrolamiento(Date fechaEnrolamiento) {
        this.fechaEnrolamiento = fechaEnrolamiento;
    }

    public String getActivo() {
        return activo;
    }

    public void setActivo(String activo) {
        this.activo = activo;
    }
}
