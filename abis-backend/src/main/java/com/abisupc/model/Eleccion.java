package com.abisupc.model;

import java.time.LocalDateTime;

/**
 * Representa una jornada electoral en el sistema.
 *
 * <p>El ciclo de vida de una eleccion esta controlado por {@link EstadoEleccion}:
 * {@code PROGRAMADA} → {@code EN_CURSO} → {@code CERRADA}. Las transiciones
 * las gestiona {@code EleccionLifecycleService}.
 * Tabla Oracle: {@code ELECCIONES}.
 */
public class Eleccion extends Entity {

    private String nombre;
    private LocalDateTime fechaHoraInicio;
    private LocalDateTime fechaHoraFin;
    private EstadoEleccion estado;

    public String getNombre() {
        return nombre;
    }
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public LocalDateTime getFechaHoraInicio() {
        return fechaHoraInicio;
    }
    public void setFechaHoraInicio(LocalDateTime fechaHoraInicio) {
        this.fechaHoraInicio = fechaHoraInicio;
    }

    public LocalDateTime getFechaHoraFin() {
        return fechaHoraFin;
    }
    public void setFechaHoraFin(LocalDateTime fechaHoraFin) {
        this.fechaHoraFin = fechaHoraFin;
    }

    public EstadoEleccion getEstado() {
        return estado;
    }
    public void setEstado(EstadoEleccion estado) {
        this.estado = estado;
    }
}