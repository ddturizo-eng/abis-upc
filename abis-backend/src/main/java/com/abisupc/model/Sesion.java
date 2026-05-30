package com.abisupc.model;

import java.time.LocalDateTime;

/**
 * Representa una sesion activa de un administrador en el sistema.
 *
 * <p>Una sesion se considera activa mientras {@code fechaFin} sea {@code null}.
 * Al cerrar sesion, {@code SesionRepository.invalidarToken()} escribe la hora
 * de cierre sin eliminar el registro, conservando el historial de accesos.
 * Tabla Oracle: {@code SESIONES}.
 */
public class Sesion extends Entity {

    private String token;
    private LocalDateTime fechaInicio;
    private Long idAdministrador;
    private LocalDateTime fechaFin;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDateTime fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public Long getIdAdministrador() {
        return idAdministrador;
    }

    public void setIdAdministrador(Long idAdministrador) {
        this.idAdministrador = idAdministrador;
    }

    public LocalDateTime getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDateTime fechaFin) {
        this.fechaFin = fechaFin;
    }
}