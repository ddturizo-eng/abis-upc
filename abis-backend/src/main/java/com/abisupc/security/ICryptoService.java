package com.abisupc.security;

/**
 * Contrato para el servicio de cifrado simetrico del sistema.
 *
 * <p>Define las operaciones de cifrado y descifrado de texto que
 * {@link CryptoService} implementa usando AES-GCM. Cualquier componente
 * que necesite cifrar datos sensibles debe depender de esta interfaz,
 * no de la implementacion concreta, para facilitar pruebas y cambios
 * de algoritmo sin afectar el resto del sistema.
 */
public interface ICryptoService {

    /**
     * Cifra un texto plano y retorna el resultado en Base64.
     *
     * @param texto texto a cifrar
     * @return texto cifrado en Base64 (IV + ciphertext)
     * @throws Exception si falla el cifrado o el KeyStore no esta disponible
     */
    String cifrarTexto(String texto) throws Exception;

    /**
     * Descifra un texto previamente cifrado con {@link #cifrarTexto(String)}.
     *
     * @param textoCifrado texto cifrado en Base64
     * @return texto plano original
     * @throws Exception si el texto esta corrupto o la clave no coincide
     */
    String descifrarTexto(String textoCifrado) throws Exception;
}