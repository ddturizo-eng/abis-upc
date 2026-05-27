package com.abisupc.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Estados posibles del ciclo de vida de una eleccion.
 *
 * <p>Las transiciones validas son: {@code PROGRAMADA} → {@code EN_CURSO} → {@code CERRADA}.
 * {@code @JsonValue} hace que Jackson serialice el enum con {@code dbValue}
 * (ej: {@code "EN_CURSO"}) en lugar del nombre interno del enum.
 * {@link #fromDb(String)} permite deserializar valores de Oracle que pueden
 * venir con espacios o en minusculas.
 */
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

    /**
     * Convierte un valor de Oracle al enum correspondiente.
     *
     * <p>Normaliza el valor recibido: elimina espacios, convierte a mayusculas
     * y reemplaza espacios por guiones bajos antes de comparar. Esto permite
     * manejar variantes como {@code "en curso"} o {@code "EN CURSO"}.
     *
     * @param value valor de la columna {@code ESTADO} en Oracle
     * @return enum correspondiente al valor
     * @throws IllegalArgumentException si el valor no corresponde a ningun estado valido
     */
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