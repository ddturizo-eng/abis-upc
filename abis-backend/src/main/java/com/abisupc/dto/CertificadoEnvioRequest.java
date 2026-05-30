package com.abisupc.dto;

/**
 * Payload interno para solicitar al microservicio Node el certificado del votante.
 * Payload interno para solicitar al microservicio de email (Node.js :8010)
 * la generacion y envio del certificado de participacion del votante.
 *
 * <p>Lo construye {@code CertificadoService} con los datos del votante,
 * la eleccion y el puesto donde voto, y lo envia via {@code CertificadoClient}.
 * {@code codigoCertificado} es un UUID unico que identifica el certificado
 * emitido y permite verificarlo posteriormente.
 */
public class CertificadoEnvioRequest {

    private String identificacion;
    private String nombre;
    private String correo;
    private Long idEleccion;
    private String nombreEleccion;
    private String fechaVoto;
    private String codigoCertificado;
    private String nombrePuesto;
    private String sede;
    private String ciudad;

    public String getIdentificacion() {
        return identificacion;
    }

    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
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

    public Long getIdEleccion() {
        return idEleccion;
    }

    public void setIdEleccion(Long idEleccion) {
        this.idEleccion = idEleccion;
    }

    public String getNombreEleccion() {
        return nombreEleccion;
    }

    public void setNombreEleccion(String nombreEleccion) {
        this.nombreEleccion = nombreEleccion;
    }

    public String getFechaVoto() {
        return fechaVoto;
    }

    public void setFechaVoto(String fechaVoto) {
        this.fechaVoto = fechaVoto;
    }

    public String getCodigoCertificado() {
        return codigoCertificado;
    }

    public void setCodigoCertificado(String codigoCertificado) {
        this.codigoCertificado = codigoCertificado;
    }

    public String getNombrePuesto() {
        return nombrePuesto;
    }

    public void setNombrePuesto(String nombrePuesto) {
        this.nombrePuesto = nombrePuesto;
    }

    public String getSede() {
        return sede;
    }

    public void setSede(String sede) {
        this.sede = sede;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }
}
