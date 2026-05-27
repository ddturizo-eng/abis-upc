package com.abisupc.model;

/**
 * Representa un administrador del sistema electoral.
 *
 * <p>El campo {@code passwordHash} almacena unicamente el hash de la
 * contrasena generado por {@code HashingService} — nunca el valor en
 * texto plano. Tabla Oracle: {@code ADMINISTRADORES}.
 */
public class Administrador extends Entity {

    private String usuario;
    private String passwordHash;
    private String nombre;
    private String correo;

    public String getUsuario() {
        return usuario;
    }
    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNombre() {
        return nombre;
    }
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCorreo() {
        return correo;
    }
    public void setCorreo(String correo) {
        this.correo = correo;
    }
}