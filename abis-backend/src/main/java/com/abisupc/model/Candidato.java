package com.abisupc.model;

public class Candidato extends Entity {

    private String primerNombre;
    private String segundoNombre;
    private String primerApellido;
    private String segundoApellido;
    private String numeroCampania;
    private Long idEleccion;
    private String cargo;

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

    public String getNumeroCampania() {

        return numeroCampania;
    }

    public void setNumeroCampania(String numeroCampania) {

        this.numeroCampania = numeroCampania;
    }

    public Long getIdEleccion() {

        return idEleccion;
    }

    public void setIdEleccion(Long idEleccion) {

        this.idEleccion = idEleccion;
    }

    public String getCargo() {

        return cargo;
    }

    public void setCargo(String cargo) {

        this.cargo = cargo;
    }
}