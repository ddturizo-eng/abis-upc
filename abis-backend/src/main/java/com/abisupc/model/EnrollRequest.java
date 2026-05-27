package com.abisupc.model;

/**
 * Cuerpo de la peticion para iniciar el enrolamiento biometrico de un votante.
 *
 * <p>Lo recibe {@code EnrollController} desde el frontend de registro.
 * Si {@code re_enroll} es {@code true}, el microservicio biometrico
 * sobreescribe la plantilla existente del votante en lugar de rechazar
 * la solicitud por duplicado.
 */
public class EnrollRequest {

    /** Cedula del votante a enrolar. */
    public String identificacion;

    /**
     * Indica si se debe sobreescribir una plantilla biometrica existente.
     * Usar {@code true} solo en casos de re-enrolamiento autorizado por el administrador.
     */
    public boolean re_enroll;
}