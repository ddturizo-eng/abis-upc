package com.abisupc.model;

public class Rol extends Entity {
    private String nombre;
    private double pesoVoto;

    public String getNombre() {

        return nombre;
    }
    public void setNombre(String nombre) {

        this.nombre = nombre;
    }

    public double getPesoVoto() {

        return pesoVoto;
    }
    public void setPesoVoto(double pesoVoto) {

        this.pesoVoto = pesoVoto;
    }
}