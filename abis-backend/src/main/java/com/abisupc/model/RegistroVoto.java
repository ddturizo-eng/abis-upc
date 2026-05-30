package com.abisupc.model;

import java.time.LocalDateTime;

/**
 * Representa el registro de participacion de un votante en una eleccion.
 *
 * <p>Por diseno de anonimato, {@code RegistroVoto} almacena la identificacion
 * del votante pero NO el candidato seleccionado. La tabla {@code VOTOS}
 * (representada por {@link Voto}) almacena el candidato pero NO la
 * identificacion. Este diseno garantiza anonimato irreversible incluso
 * con acceso directo a la base de datos. Tabla Oracle: {@code REGISTRO_VOTOS}.
 */
public class RegistroVoto extends Entity {

    private LocalDateTime fechaHora;
    private String identificacion;
    private Long idPuesto;
    private Long idEleccion;

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }
    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public String getIdentificacion() {
        return identificacion;
    }
    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
    }

    public Long getIdPuesto() {
        return idPuesto;
    }
    public void setIdPuesto(Long idPuesto) {
        this.idPuesto = idPuesto;
    }

    public Long getIdEleccion() {
        return idEleccion;
    }
    public void setIdEleccion(Long idEleccion) {
        this.idEleccion = idEleccion;
    }
}