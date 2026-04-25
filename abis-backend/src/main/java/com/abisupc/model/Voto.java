package com.abisupc.model;

import java.time.LocalDateTime;

public class Voto extends Entity {
    private Long idRol;
    private Long idEleccion;
    private Long idCandidato;
    private LocalDateTime fechaHora;
    private double pesoVotoAplicado;

    public Long getIdRol() {
        return idRol;
    }

    public void setIdRol(Long idRol) {
        this.idRol = idRol;
    }

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

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public double getPesoVotoAplicado() {
        return pesoVotoAplicado;
    }

    public void setPesoVotoAplicado(double pesoVotoAplicado) {
        this.pesoVotoAplicado = pesoVotoAplicado;
    }
}