package com.abisupc.model;

import java.sql.Timestamp;

public class Voto extends Entity {
    private Long idEleccion;
    private Long idCandidato;
    private Timestamp fechaHora;
    private double pesoVotoAplicado;

    public Long getIdEleccion() {
        return idEleccion;
    }

    public void setIdEleccion(Long idEleccion) {
        this.idEleccion = idEleccion;
    }

    public Long getIdCandidato() {
        return idCandidato;
    }

    public void setIdCandidato(Long idCandidato) {
        this.idCandidato = idCandidato;
    }

    public Timestamp getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(Timestamp fechaHora) {
        this.fechaHora = fechaHora;
    }

    public double getPesoVotoAplicado() {
        return pesoVotoAplicado;
    }

    public void setPesoVotoAplicado(double pesoVotoAplicado) {
        this.pesoVotoAplicado = pesoVotoAplicado;
    }
}
