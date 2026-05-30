package com.abisupc.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Servicio de cifrado simetrico AES-256-GCM para datos sensibles del sistema.
 *
 * <p>Usa AES en modo GCM (Galois/Counter Mode) con autenticacion integrada,
 * lo que garantiza tanto confidencialidad como integridad del dato cifrado.
 * Cualquier modificacion del ciphertext es detectada al descifrar.
 *
 * <p>Cada operacion de cifrado genera un IV (vector de inicializacion) aleatorio
 * de {@value IV_SIZE} bytes via {@link SecureRandom}. El IV se antepone al
 * ciphertext en el resultado, por lo que dos cifrados del mismo texto producen
 * salidas distintas — esto es intencional y necesario para seguridad semantica.
 *
 * <p>La clave AES se obtiene de {@link KeyStoreManager}, que la carga desde
 * un KeyStore JCEKS protegido por variables de entorno.
 */
public class CryptoService implements ICryptoService {

    /** Tamano del IV en bytes para AES-GCM. */
    private static final int IV_SIZE = 12;

    /** Longitud en bits del tag de autenticacion GCM. */
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * Genera un IV aleatorio criptograficamente seguro.
     *
     * @return arreglo de {@value IV_SIZE} bytes aleatorios
     */
    private byte[] generarIV() {
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * Construye un {@link Cipher} AES-GCM configurado para cifrar o descifrar.
     *
     * @param modo {@link Cipher#ENCRYPT_MODE} o {@link Cipher#DECRYPT_MODE}
     * @param iv   vector de inicializacion de {@value IV_SIZE} bytes
     * @return cipher listo para usar
     * @throws Exception si la clave o el algoritmo no estan disponibles
     */
    private Cipher construirCipher(int modo, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(modo, KeyStoreManager.getInstance().getAesKey(), spec);
        return cipher;
    }

    /**
     * Cifra un arreglo de bytes con AES-GCM.
     *
     * <p>El resultado tiene el formato: {@code [IV (12 bytes)][ciphertext]}.
     * El IV se incluye en el resultado para que {@link #descifrar(byte[])}
     * pueda extraerlo sin necesidad de almacenarlo por separado.
     *
     * @param datos bytes a cifrar
     * @return IV concatenado con ciphertext
     * @throws Exception si falla el cifrado
     */
    public byte[] cifrar(byte[] datos) throws Exception {
        byte[] iv = generarIV();
        Cipher cipher = construirCipher(Cipher.ENCRYPT_MODE, iv);
        byte[] ciphertext = cipher.doFinal(datos);

        byte[] resultado = new byte[IV_SIZE + ciphertext.length];
        System.arraycopy(iv, 0, resultado, 0, IV_SIZE);
        System.arraycopy(ciphertext, 0, resultado, IV_SIZE, ciphertext.length);
        return resultado;
    }

    /**
     * Descifra un arreglo de bytes cifrado con {@link #cifrar(byte[])}.
     *
     * <p>Extrae los primeros {@value IV_SIZE} bytes como IV y el resto
     * como ciphertext, luego descifra con AES-GCM. Si el ciphertext fue
     * alterado, GCM lanza una excepcion de autenticacion.
     *
     * @param datosCifrados IV concatenado con ciphertext
     * @return bytes originales descifrados
     * @throws Exception si el ciphertext esta corrupto o la clave no coincide
     */
    public byte[] descifrar(byte[] datosCifrados) throws Exception {
        byte[] iv = new byte[IV_SIZE];
        System.arraycopy(datosCifrados, 0, iv, 0, IV_SIZE);

        byte[] ciphertext = new byte[datosCifrados.length - IV_SIZE];
        System.arraycopy(datosCifrados, IV_SIZE, ciphertext, 0, ciphertext.length);

        Cipher cipher = construirCipher(Cipher.DECRYPT_MODE, iv);
        return cipher.doFinal(ciphertext);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Convierte el texto a bytes, cifra con {@link #cifrar(byte[])}
     * y retorna el resultado en Base64 para almacenamiento o transporte.
     */
    @Override
    public String cifrarTexto(String texto) throws Exception {
        byte[] cifrado = cifrar(texto.getBytes());
        return Base64.getEncoder().encodeToString(cifrado);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Decodifica el Base64, descifra con {@link #descifrar(byte[])}
     * y retorna el texto plano original.
     */
    @Override
    public String descifrarTexto(String textoCifrado) throws Exception {
        byte[] datos = Base64.getDecoder().decode(textoCifrado);
        return new String(descifrar(datos));
    }
}