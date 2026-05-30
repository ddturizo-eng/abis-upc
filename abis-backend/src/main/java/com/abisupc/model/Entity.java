package com.abisupc.model;

/**
 * Clase base para todas las entidades del dominio que tienen
 * identificador primario numerico en Oracle.
 *
 * <p>Las entidades con PK compuesta, como {@link Jurado}, no extienden
 * esta clase porque no tienen un unico {@code Long id} que las identifique.
 */
public class Entity {
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}