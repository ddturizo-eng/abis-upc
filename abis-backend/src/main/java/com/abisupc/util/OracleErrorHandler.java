package com.abisupc.util;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traduce errores Oracle ORA-20XXX a respuestas HTTP consistentes.
 */
public final class OracleErrorHandler {

    private static final Pattern ORA_PATTERN = Pattern.compile("ORA-(20\\d{3}):\\s*([^\\r\\n]*)");

    private OracleErrorHandler() {
    }

    public static Optional<OracleError> from(SQLException exception) {
        if (exception == null) {
            return Optional.empty();
        }

        int code = extractCode(exception);
        if (code < 20000 || code > 20999) {
            return Optional.empty();
        }

        return Optional.of(new OracleError(code, statusCode(code), message(exception, code)));
    }

    public static Optional<OracleError> from(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return from(sqlException);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    private static int extractCode(SQLException exception) {
        int errorCode = exception.getErrorCode();
        if (errorCode >= 20000 && errorCode <= 20999) {
            return errorCode;
        }

        Matcher matcher = ORA_PATTERN.matcher(fullMessage(exception));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : errorCode;
    }

    private static int statusCode(int oraCode) {
        if (oraCode == 20070 || inRange(oraCode, 20090, 20093)) {
            return 403;
        }

        if (oraCode == 20001 ||
                inRange(oraCode, 20020, 20022) ||
                inRange(oraCode, 20050, 20051) ||
                inRange(oraCode, 20060, 20062) ||
                inRange(oraCode, 20064, 20064) ||
                inRange(oraCode, 20080, 20080) ||
                inRange(oraCode, 20200, 20201)) {
            return 409;
        }

        if (oraCode == 20063) {
            return 400;
        }

        return 400;
    }

    private static String message(SQLException exception, int code) {
        Matcher matcher = ORA_PATTERN.matcher(fullMessage(exception));
        if (matcher.find() && !matcher.group(2).isBlank()) {
            return matcher.group(2).trim();
        }
        return defaultMessage(code);
    }

    private static String fullMessage(SQLException exception) {
        StringBuilder message = new StringBuilder();
        SQLException current = exception;
        while (current != null) {
            if (current.getMessage() != null) {
                message.append(current.getMessage()).append('\n');
            }
            current = current.getNextException();
        }
        return message.toString();
    }

    private static boolean inRange(int value, int start, int end) {
        return value >= start && value <= end;
    }

    private static String defaultMessage(int code) {
        return switch (code) {
            case 20001 -> "El voto no puede registrarse porque la eleccion no esta en curso.";
            case 20020 -> "El votante se encuentra inhabilitado.";
            case 20021 -> "El votante ya ejercio su voto.";
            case 20022 -> "El registro de voto ya existe.";
            case 20050 -> "La eleccion no esta programada para inscribir candidatos.";
            case 20051 -> "El numero de tarjeton ya esta asignado.";
            case 20060 -> "El candidato no puede ser jurado.";
            case 20061 -> "El jurado se encuentra inhabilitado.";
            case 20062 -> "Solo DOCENTES y ADMINISTRATIVOS pueden ser jurados.";
            case 20063 -> "El votante no tiene fecha de nacimiento registrada.";
            case 20064 -> "El votante debe tener al menos 30 anos para ser jurado.";
            case 20080 -> "El votante ya es jurado en otra mesa de esta misma eleccion.";
            case 20070 -> "El votante no puede votar.";
            case 20090, 20091 -> "El voto es inmutable segun Art. 258.";
            case 20092, 20093 -> "El registro de participacion es inmutable.";
            default -> defaultRangeMessage(code);
        };
    }

    private static String defaultRangeMessage(int code) {
        if (inRange(code, 20100, 20103)) {
            return "La solicitud de inhabilitacion es invalida.";
        }
        if (inRange(code, 20110, 20113)) {
            return "La solicitud de habilitacion es invalida.";
        }
        if (inRange(code, 20130, 20133)) {
            return "La solicitud de biometria es invalida.";
        }
        if (inRange(code, 20140, 20144)) {
            return "La asignacion de jurado es invalida.";
        }
        if (code == 20200) {
            return "Esta identificacion no pertenece al censo institucional de la UPC.";
        }
        if (code == 20201) {
            return "Este usuario esta inactivo en el sistema institucional.";
        }
        return "Error de regla de negocio Oracle.";
    }

    public record OracleError(int oraCode, int statusCode, String message) {
    }
}
