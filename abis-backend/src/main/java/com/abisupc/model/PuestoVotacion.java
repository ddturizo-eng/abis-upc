package com.abisupc.model;

import java.sql.Timestamp;

public class PuestoVotacion extends Entity {

    private String ciudad;
    private String sede;
    private String nombrePuesto;
    private Timestamp horaInicio;
    private Timestamp horaSalida;

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

    public Timestamp getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(Timestamp horaInicio) {
        this.horaInicio = horaInicio;
    }

    public Timestamp getHoraSalida() {
        return horaSalida;
    }

    public void setHoraSalida(Timestamp horaSalida) {
        this.horaSalida = horaSalida;
    }
}
