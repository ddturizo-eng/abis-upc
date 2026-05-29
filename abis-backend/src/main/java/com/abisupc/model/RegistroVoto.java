package com.abisupc.model;

import java.time.LocalDateTime;

public class RegistroVoto extends Entity {

    private LocalDateTime fechaHora;
    private String identificacion;
    private Long idPuesto;
    private Long idEleccion;

    public LocalDateTime getFechaHora() {

        return fechaHora;
    }
    public void setFechaHora(LocalDateTime fechaHora) {

        this.fechaHora = fechaHora;
    }

    public String getIdentificacion() {

        return identificacion;
    }
    public void setIdentificacion(String identificacion) {

        this.identificacion = identificacion;
    }

    public Long getIdPuesto() {

        return idPuesto;
    }
    public void setIdPuesto(Long idPuesto) {

        this.idPuesto = idPuesto;
    }

    public Long getIdEleccion() {

        return idEleccion;
    }
    public void setIdEleccion(Long idEleccion) {

        this.idEleccion = idEleccion;
    }
}