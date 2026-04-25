package com.abisupc.model;

public class PuestoVotacion extends Entity {

    private String ciudad;
    private String sede;
    private String nombrePuesto;

    public String getCiudad() {

        return ciudad;
    }
    public void setCiudad(String ciudad) {

        this.ciudad = ciudad;
    }

    public String getSede() {

        return sede;
    }
    public void setSede(String sede) {

        this.sede = sede;
    }

    public String getNombrePuesto() {

        return nombrePuesto;
    }
    public void setNombrePuesto(String nombrePuesto) {

        this.nombrePuesto = nombrePuesto;
    }
}