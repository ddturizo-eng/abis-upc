package com.abisupc.model;

import java.util.Date;

/**
 * Representa la configuracion del peso de voto de un rol en una eleccion especifica.
 *
 * <p>Permite que el peso de voto de un rol sea distinto en cada eleccion,
 * independiente del valor global en {@code ROLES.PESO_VOTO}.
 * Tabla Oracle: {@code ELECCION_ROLES}.
 */
public class EleccionRol {

    private Long idEleccion;
    private Long idRol;
    private Double pesoVoto;
    private Date fechaConfiguracion;
    private String nombreRol;

    public Long getIdEleccion() {
        return idEleccion;
    }
    public void setIdEleccion(Long idEleccion) {
        this.idEleccion = idEleccion;
    }

    public Long getIdRol() {
        return idRol;
    }
    public void setIdRol(Long idRol) {
        this.idRol = idRol;
    }

    public Double getPesoVoto() {
        return pesoVoto;
    }
    public void setPesoVoto(Double pesoVoto) {
        this.pesoVoto = pesoVoto;
    }

    public Date getFechaConfiguracion() {
        return fechaConfiguracion;
    }
    public void setFechaConfiguracion(Date fechaConfiguracion) {
        this.fechaConfiguracion = fechaConfiguracion;
    }

    public String getNombreRol() {
        return nombreRol;
    }
    public void setNombreRol(String nombreRol) {
        this.nombreRol = nombreRol;
    }
}