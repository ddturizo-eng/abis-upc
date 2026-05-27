package com.abisupc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo de la peticion para registrar un nuevo votante en el sistema.
 *
 * <p>Lo recibe {@code RegistroController} desde el formulario del paso 4
 * del flujo de registro. {@code @JsonIgnoreProperties(ignoreUnknown = true)}
 * permite tolerar campos adicionales del frontend sin lanzar error de
 * deserializacion. Los campos {@code idRol} e {@code idPuesto} referencian
 * las PKs de {@code ROLES} y {@code PUESTOS_VOTACION} respectivamente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegistroRequest {

    @JsonProperty("identificacion")
    public String identificacion;

    @JsonProperty("primer_nombre")
    public String primerNombre;

    @JsonProperty("segundo_nombre")
    public String segundoNombre;

    @JsonProperty("primer_apellido")
    public String primerApellido;

    @JsonProperty("segundo_apellido")
    public String segundoApellido;

    @JsonProperty("correo")
    public String correo;

    @JsonProperty("id_rol")
    public Long idRol;

    @JsonProperty("id_puesto")
    public Long idPuesto;

    @JsonProperty("fecha_nacimiento")
    public String fechaNacimiento;

    @JsonProperty("qr_cedula")
    public String qrCedula;
}