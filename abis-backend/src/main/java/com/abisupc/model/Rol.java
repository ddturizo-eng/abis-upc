package com.abisupc.model;

/**
 * Representa un rol del sistema electoral (ej: ESTUDIANTE, DOCENTE).
 *
 * <p>El rol determina el peso del voto de un votante en una eleccion.
 * Tabla Oracle: {@code ROLES}.
 */
public class Rol extends Entity {

    private String nombre;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
}