package com.abisupc.model;

import java.time.LocalDateTime;

public class Votante extends Entity {

    private String identificacion;
    private String plantillaBiometrica;
    private String correo;
    private String primerNombre;
    private String segundoNombre;
    private String primerApellido;
    private String segundoApellido;
    private EstadoVotante estadoVoto;
    private String fotoUrl;
    private LocalDateTime fechaConsentimiento;
    private String hashIntegridadBiometrica;
    private Long idRol;
    private Long idPuesto;

    public String getIdentificacion() {
        return identificacion;
    }

    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
    }

    public String getPlantillaBiometrica() {
        return plantillaBiometrica;
    }

    public void setPlantillaBiometrica(String plantillaBiometrica) {
        this.plantillaBiometrica = plantillaBiometrica;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getPrimerNombre() {
        return primerNombre;
    }

    public void setPrimerNombre(String primerNombre) {
        this.primerNombre = primerNombre;
    }

    public String getSegundoNombre() {
        return segundoNombre;
    }

    public void setSegundoNombre(String segundoNombre) {
        this.segundoNombre = segundoNombre;
    }

    public String getPrimerApellido() {
        return primerApellido;
    }

    public void setPrimerApellido(String primerApellido) {
        this.primerApellido = primerApellido;
    }

    public String getSegundoApellido() {
        return segundoApellido;
    }

    public void setSegundoApellido(String segundoApellido) {
        this.segundoApellido = segundoApellido;
    }

    public EstadoVotante getEstadoVoto() {
        return estadoVoto;
    }

    public void setEstadoVoto(EstadoVotante estadoVoto) {
        this.estadoVoto = estadoVoto;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    public LocalDateTime getFechaConsentimiento() {
        return fechaConsentimiento;
    }

    public void setFechaConsentimiento(LocalDateTime fechaConsentimiento) {
        this.fechaConsentimiento = fechaConsentimiento;
    }

    public String getHashIntegridadBiometrica() {
        return hashIntegridadBiometrica;
    }

    public void setHashIntegridadBiometrica(String hashIntegridadBiometrica) {
        this.hashIntegridadBiometrica = hashIntegridadBiometrica;
    }

    public Long getIdRol() {
        return idRol;
    }

    public void setIdRol(Long idRol) {
        this.idRol = idRol;
    }

    public Long getIdPuesto() {
        return idPuesto;
    }

    public void setIdPuesto(Long idPuesto) {
        this.idPuesto = idPuesto;
    }
}