package com.abisupc.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Representa la asignacion de un votante como jurado en una mesa.
 *
 * <p>No extiende {@link Entity} porque su identidad en Oracle es una
 * PK compuesta: {@code ID_MESA} + {@code IDENTIFICACION}. No existe
 * un unico {@code Long id} que la identifique de forma independiente.
 *
 * <p>Tabla Oracle: {@code JURADOS}.
 */
public class Jurado {

    private Long idMesa;
    private String identificacion;
    private LocalDate fechaAsignacion;
    private String cargo;

    public Long getIdMesa() {
        return idMesa;
    }
    public void setIdMesa(Long idMesa) {
        this.idMesa = idMesa;
    }

    public String getIdentificacion() {
        return identificacion;
    }
    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
    }

    public LocalDate getFechaAsignacion() {
        return fechaAsignacion;
    }
    public void setFechaAsignacion(LocalDate fechaAsignacion) {
        this.fechaAsignacion = fechaAsignacion;
    }

    public String getCargo() {
        return cargo;
    }
    public void setCargo(String cargo) {
        this.cargo = cargo;
    }
}