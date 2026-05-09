package com.abisupc.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.sql.Timestamp;

public class Votante extends Entity {

    @JsonProperty("identificacion")
    private String identificacion;

    @JsonProperty("plantilla_biometrica")
    private String plantillaBiometrica;

    @JsonProperty("correo")
    private String correo;

    @JsonProperty("primer_nombre")
    private String primerNombre;

    @JsonProperty("segundo_nombre")
    private String segundoNombre;

    @JsonProperty("primer_apellido")
    private String primerApellido;

    @JsonProperty("segundo_apellido")
    private String segundoApellido;

    @JsonProperty("estado_voto")
    private String estadoVoto;

    @JsonProperty("foto_url")
    private String fotoUrl;

    @JsonProperty("fecha_consentimiento")
    private Timestamp fechaConsentimiento;

    @JsonProperty("hash_integridad_biometrica")
    private String hashIntegridadBiometrica;

    @JsonProperty("rol_id")
    private Long idRol;

    @JsonProperty("puesto_id")
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

    public String getEstadoVoto() {
        return estadoVoto;
    }

    public void setEstadoVoto(String estadoVoto) {
        this.estadoVoto = estadoVoto;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    public Timestamp getFechaConsentimiento() {
        return fechaConsentimiento;
    }

    public void setFechaConsentimiento(Timestamp fechaConsentimiento) {
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