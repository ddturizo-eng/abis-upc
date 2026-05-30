package com.abisupc.dto;

/**
 * Envoltorio generico para todas las respuestas HTTP del backend.
 *
 * <p>Estandariza la estructura JSON que recibe el frontend:
 * <pre>
 * {
 *   "success": true,
 *   "message": null,
 *   "data": { ... }
 * }
 * </pre>
 *
 * <p>Los controllers usan los metodos de fabrica {@link #success(Object)}
 * y {@link #error(String)} en lugar del constructor directamente, para
 * mantener consistencia en toda la API.
 *
 * @param <T> tipo del payload incluido en {@code data}
 */
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    public ApiResponse() {}

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    /**
     * Crea una respuesta exitosa con el payload indicado.
     *
     * @param data objeto a retornar en el campo {@code data}
     * @param <T>  tipo del payload
     * @return respuesta con {@code success = true} y {@code message = null}
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data);
    }

    /**
     * Crea una respuesta de error con el mensaje indicado.
     *
     * @param message descripcion del error legible para el frontend
     * @param <T>     tipo del payload (siempre {@code null} en errores)
     * @return respuesta con {@code success = false} y {@code data = null}
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }
    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }
    public void setData(T data) {
        this.data = data;
    }
}