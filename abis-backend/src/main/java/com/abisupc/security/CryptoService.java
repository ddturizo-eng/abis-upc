package com.abisupc.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;

public class CryptoService implements ICryptoService {

    private static final int IV_SIZE = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private byte[] generarIV() {
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    private Cipher construirCipher(int modo, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(modo, KeyStoreManager.getInstance().getAesKey(), spec);
        return cipher;
    }

    public byte[] cifrar(byte[] datos) throws Exception {
        byte[] iv = generarIV();
        Cipher cipher = construirCipher(Cipher.ENCRYPT_MODE, iv);
        byte[] ciphertext = cipher.doFinal(datos);

        byte[] resultado = new byte[IV_SIZE + ciphertext.length];
        System.arraycopy(iv, 0, resultado, 0, IV_SIZE);
        System.arraycopy(ciphertext, 0, resultado, IV_SIZE, ciphertext.length);
        return resultado;
    }

    public byte[] descifrar(byte[] datosCifrados) throws Exception {
        byte[] iv = new byte[IV_SIZE];
        System.arraycopy(datosCifrados, 0, iv, 0, IV_SIZE);

        byte[] ciphertext = new byte[datosCifrados.length - IV_SIZE];
        System.arraycopy(datosCifrados, IV_SIZE, ciphertext, 0, ciphertext.length);

        Cipher cipher = construirCipher(Cipher.DECRYPT_MODE, iv);
        return cipher.doFinal(ciphertext);
    }

    @Override
    public String cifrarTexto(String texto) throws Exception {
        byte[] cifrado = cifrar(texto.getBytes());
        return Base64.getEncoder().encodeToString(cifrado);
    }

    @Override
    public String descifrarTexto(String textoCifrado) throws Exception {
        byte[] datos = Base64.getDecoder().decode(textoCifrado);
        return new String(descifrar(datos));
    }
}