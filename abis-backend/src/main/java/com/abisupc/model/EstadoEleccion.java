package com.abisupc.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EstadoEleccion {
    PROGRAMADA("PROGRAMADA"),
    EN_CURSO("EN_CURSO"),
    CERRADA("CERRADA");

    private final String dbValue;

    EstadoEleccion(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    public static EstadoEleccion fromDb(String value) {
        if (value == null) {
            return null;
        }

        String normalizado = value.trim().toUpperCase().replace(' ', '_');
        for (EstadoEleccion estado : values()) {
            if (estado.dbValue.equals(normalizado) || estado.name().equals(normalizado)) {
                return estado;
            }
        }

        throw new IllegalArgumentException("Estado de eleccion no valido: " + value);
    }
}
