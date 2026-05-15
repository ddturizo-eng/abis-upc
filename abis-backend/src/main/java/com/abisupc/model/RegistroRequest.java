package com.abisupc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonProperty("qr_cedula")
    public String qrCedula;
}
