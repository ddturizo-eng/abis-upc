package com.abisupc.security;

/**
 * Contrato para el servicio de hashing del sistema.
 *
 * <p>Define las operaciones de hash y verificacion que {@link HashingService}
 * implementa usando SHA-256. Se usa para proteger contrasenas de administradores
 * y verificar la integridad de plantillas biometricas almacenadas en Oracle.
 */
public interface IHashingService {

    /**
     * Genera el hash SHA-256 de una contrasena en formato hexadecimal.
     *
     * <p>El resultado se almacena en {@code ADMINISTRADORES.PASSWORD_HASH}.
     * Nunca se almacena la contrasena en texto plano.
     *
     * @param rawPassword contrasena en texto plano
     * @return hash SHA-256 en formato hexadecimal
     * @throws Exception si el algoritmo SHA-256 no esta disponible
     */
    String hashPassword(String rawPassword) throws Exception;

    /**
     * Verifica si una contrasena en texto plano coincide con su hash almacenado.
     *
     * <p>Usa {@link java.security.MessageDigest#isEqual} para comparacion
     * en tiempo constante, evitando ataques de timing.
     *
     * @param rawPassword     contrasena ingresada por el usuario
     * @param hashAlmacenado  hash hexadecimal almacenado en Oracle
     * @return {@code true} si la contrasena coincide con el hash
     * @throws Exception si el algoritmo SHA-256 no esta disponible
     */
    boolean verificarPassword(String rawPassword, String hashAlmacenado) throws Exception;

    /**
     * Verifica la integridad de una plantilla biometrica comparando su hash.
     *
     * <p>El hash de la plantilla se almacena en Base64. Se usa para detectar
     * si una plantilla fue modificada despues de su enrolamiento.
     *
     * @param template       plantilla biometrica en bytes
     * @param hashAlmacenado hash SHA-256 en Base64 almacenado en Oracle
     * @return {@code true} si la plantilla no ha sido alterada
     * @throws Exception si el algoritmo SHA-256 no esta disponible
     */
    boolean verificarIntegridad(byte[] template, String hashAlmacenado) throws Exception;
}