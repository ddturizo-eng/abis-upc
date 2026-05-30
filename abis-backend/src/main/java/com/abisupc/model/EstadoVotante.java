package com.abisupc.model;

/**
 * Estados posibles del votante respecto a su participacion en una eleccion.
 *
 * <ul>
 *   <li>{@code PENDIENTE} — habilitado para votar, aun no ha ejercido su derecho</li>
 *   <li>{@code EJERCIDO} — ya emitio su voto en la jornada actual</li>
 *   <li>{@code INHABILITADO} — no puede participar en esta eleccion</li>
 * </ul>
 */
public enum EstadoVotante {
    PENDIENTE,
    EJERCIDO,
    INHABILITADO
}