package com.abisupc.model;

/**
 * Postulación de un candidato en una elección específica. Permite que un candidato se postule en múltiples elecciones.
 */
public class CandidatoEleccion {

    private Long idCandidato;
    private Long idEleccion;
    private Integer numeroCampania;
    private String cargo;
    private String primerNombre;
    private String segundoNombre;
    private String primerApellido;
    private String segundoApellido;
    private String fotoUrl;
    private Integer votos = 0;

    public Long getIdCandidato() {
        return idCandidato;
    }

    public Long getId() {
        return idCandidato;
    }

    public void setIdCandidato(Long idCandidato) {
        this.idCandidato = idCandidato;
    }

    public Long getIdEleccion() {
        return idEleccion;
    }

    public void setIdEleccion(Long idEleccion) {
        this.idEleccion = idEleccion;
    }

    public Integer getNumeroCampania() {
        return numeroCampania;
    }

    public void setNumeroCampania(Integer numeroCampania) {
        this.numeroCampania = numeroCampania;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
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

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    public Integer getVotos() {
        return votos;
    }

    public Integer getTotalVotos() {
        return votos;
    }

    public void setVotos(Integer votos) {
        this.votos = votos != null ? votos : 0;
    }
}
