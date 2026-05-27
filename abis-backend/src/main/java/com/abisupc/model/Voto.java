package com.abisupc.model;

import java.sql.Timestamp;

/**
 * Representa un voto emitido en una eleccion.
 *
 * <p>Por diseno de anonimato, {@code Voto} almacena el candidato pero
 * NO la identificacion del votante. La tabla {@code REGISTRO_VOTOS}
 * (representada por {@link RegistroVoto}) almacena la identificacion pero
 * NO el candidato. Es imposible cruzar ambas tablas para conocer el voto
 * de una persona especifica.
 *
 * <p>{@code pesoVotoAplicado} congela el peso del rol del votante en el
 * momento exacto del voto. Si el peso cambia despues, los resultados
 * historicos siguen siendo correctos. Tabla Oracle: {@code VOTOS}.
 */
public class Voto extends Entity {

    private Long idEleccion;
    private Long idCandidato;
    private Timestamp fechaHora;
    private double pesoVotoAplicado;

    public Long getIdEleccion() {
        return idEleccion;
    }
    public void setIdEleccion(Long idEleccion) {
        this.idEleccion = idEleccion;
    }

    public Long getIdCandidato() {
        return idCandidato;
    }
    public void setIdCandidato(Long idCandidato) {
        this.idCandidato = idCandidato;
    }

    public Timestamp getFechaHora() {
        return fechaHora;
    }
    public void setFechaHora(Timestamp fechaHora) {
        this.fechaHora = fechaHora;
    }

    public double getPesoVotoAplicado() {
        return pesoVotoAplicado;
    }
    public void setPesoVotoAplicado(double pesoVotoAplicado) {
        this.pesoVotoAplicado = pesoVotoAplicado;
    }
}