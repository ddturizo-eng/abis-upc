package com.abisupc.security;

import java.security.MessageDigest;
import java.util.Base64;

public final class HashingService implements IHashingService {

    private byte[] calcularHash(byte[] datos) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(datos);
    }

    public String hashTemplate(byte[] template) throws Exception {
        byte[] hash = calcularHash(template);
        return Base64.getEncoder().encodeToString(hash);
    }

    @Override
    public String hashPassword(String rawPassword) throws Exception {
        byte[] hash = calcularHash(rawPassword.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Override
    public boolean verificarPassword(String rawPassword, String hashAlmacenado) throws Exception {
        byte[] hashNuevo = calcularHash(rawPassword.getBytes());
        byte[] hashGuardado = hexToBytes(hashAlmacenado);
        return MessageDigest.isEqual(hashNuevo, hashGuardado);
    }

    @Override
    public boolean verificarIntegridad(byte[] template, String hashAlmacenado) throws Exception {
        byte[] hashNuevo = calcularHash(template);
        byte[] hashGuardado = Base64.getDecoder().decode(hashAlmacenado);
        return MessageDigest.isEqual(hashNuevo, hashGuardado);
    }

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
