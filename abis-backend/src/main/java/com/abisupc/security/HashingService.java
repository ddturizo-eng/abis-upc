package com.abisupc.security;

import java.security.MessageDigest;
import java.util.Base64;

public final class HashingService implements IHashingService {

/**
 * Implementacion del servicio de hashing usando SHA-256.
 *
 * <p>Maneja dos formatos de salida segun el caso de uso:
 * <ul>
 *   <li>Hexadecimal — para contrasenas de administradores ({@link #hashPassword})</li>
 *   <li>Base64 — para plantillas biometricas ({@link #hashTemplate})</li>
 * </ul>
 *
 * <p>Todas las comparaciones usan {@link MessageDigest#isEqual} para garantizar
 * tiempo constante y evitar ataques de timing donde un atacante podria deducir
 * el hash correcto midiendo el tiempo de respuesta.
 *
 * <p>Declarada {@code final} para evitar que subclases sobreescriban la logica
 * de seguridad de forma inadvertida.
 */
public final class HashingService implements IHashingService {

    /**
     * Calcula el hash SHA-256 de un arreglo de bytes.
     *
     * @param datos bytes a hashear
     * @return hash SHA-256 en bytes
     * @throws Exception si SHA-256 no esta disponible en el proveedor de seguridad
     */
    private byte[] calcularHash(byte[] datos) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(datos);
    }

    /**
     * Genera el hash SHA-256 de una plantilla biometrica en formato Base64.
     *
     * <p>Se almacena en Oracle junto a la plantilla cifrada para verificar
     * su integridad en operaciones futuras de verificacion biometrica.
     *
     * @param template plantilla biometrica en bytes
     * @return hash SHA-256 en Base64
     * @throws Exception si SHA-256 no esta disponible
     */
    public String hashTemplate(byte[] template) throws Exception {
        byte[] hash = calcularHash(template);
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Retorna el hash en hexadecimal (64 caracteres) para almacenar
     * en {@code ADMINISTRADORES.PASSWORD_HASH}.
     */
    @Override
    public String hashPassword(String rawPassword) throws Exception {
        byte[] hash = calcularHash(rawPassword.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Convierte el hash hexadecimal almacenado a bytes antes de comparar,
     * garantizando que la comparacion sea en tiempo constante.
     */
    @Override
    public boolean verificarPassword(String rawPassword, String hashAlmacenado) throws Exception {
        byte[] hashNuevo = calcularHash(rawPassword.getBytes());
        byte[] hashGuardado = hexToBytes(hashAlmacenado);
        return MessageDigest.isEqual(hashNuevo, hashGuardado);
    }

    /** {@inheritDoc} */
    @Override
    public boolean verificarIntegridad(byte[] template, String hashAlmacenado) throws Exception {
        byte[] hashNuevo = calcularHash(template);
        byte[] hashGuardado = Base64.getDecoder().decode(hashAlmacenado);
        return MessageDigest.isEqual(hashNuevo, hashGuardado);
    }

    /**
     * Convierte una cadena hexadecimal a su representacion en bytes.
     *
     * @param hex cadena hexadecimal de longitud par
     * @return arreglo de bytes equivalente
     */
    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
}
