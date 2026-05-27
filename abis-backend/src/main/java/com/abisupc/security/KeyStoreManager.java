package com.abisupc.security;

import javax.crypto.SecretKey;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Arrays;

/**
 * Administrador del KeyStore JCEKS que contiene la clave AES del sistema.
 *
 * <p>Implementa el patron Singleton para garantizar que el KeyStore se cargue
 * una sola vez durante el ciclo de vida de la JVM. La clave AES se mantiene
 * en memoria y nunca se serializa ni se registra en logs.
 *
 * <p>Requiere tres variables de entorno obligatorias:
 * <ul>
 *   <li>{@code ABIS_KEYSTORE_PATH} — ruta al archivo {@code .jceks}</li>
 *   <li>{@code ABIS_KEYSTORE_PASSWORD} — contrasena del KeyStore</li>
 *   <li>{@code ABIS_AES_KEY_ALIAS} — alias de la clave AES dentro del KeyStore</li>
 * </ul>
 *
 * <p>La contrasena se limpia de memoria con {@link Arrays#fill} inmediatamente
 * despues de cargar el KeyStore, reduciendo el tiempo de exposicion en RAM.
 */
public class KeyStoreManager {

    private static KeyStoreManager instance;
    private final SecretKey aesKey;

    /**
     * Constructor privado — valida variables de entorno y carga el KeyStore.
     *
     * @throws IllegalStateException si alguna variable de entorno falta
     *         o si el KeyStore no puede abrirse
     */
    private KeyStoreManager() {
        validarVariablesEntorno();
        aesKey = cargarKeyStore();
    }

    /**
     * Retorna la instancia unica de {@code KeyStoreManager}.
     *
     * <p>Sincronizado para garantizar que solo se cree una instancia
     * incluso si multiples hilos llaman a este metodo simultaneamente
     * durante el arranque del servidor.
     *
     * @return instancia singleton de {@code KeyStoreManager}
     */
    public static synchronized KeyStoreManager getInstance() {
        if (instance == null) {
            instance = new KeyStoreManager();
        }
        return instance;
    }

    /**
     * Verifica que las tres variables de entorno requeridas esten definidas.
     *
     * @throws IllegalStateException si alguna variable esta ausente
     */
    private void validarVariablesEntorno() {
        if (System.getenv("ABIS_KEYSTORE_PATH") == null)
            throw new IllegalStateException("Variable de entorno ABIS_KEYSTORE_PATH no configurada");
        if (System.getenv("ABIS_KEYSTORE_PASSWORD") == null)
            throw new IllegalStateException("Variable de entorno ABIS_KEYSTORE_PASSWORD no configurada");
        if (System.getenv("ABIS_AES_KEY_ALIAS") == null)
            throw new IllegalStateException("Variable de entorno ABIS_AES_KEY_ALIAS no configurada");
    }

    /**
     * Abre el KeyStore JCEKS y extrae la clave AES por su alias.
     *
     * <p>Limpia la contrasena de memoria inmediatamente despues de cargar
     * el KeyStore para reducir el tiempo de exposicion en RAM.
     *
     * @return clave AES lista para usar en {@link CryptoService}
     * @throws IllegalStateException si el archivo no existe, la contrasena
     *         es incorrecta, o el alias no se encuentra en el KeyStore
     */
    private SecretKey cargarKeyStore() {
        String path = System.getenv("ABIS_KEYSTORE_PATH");
        String alias = System.getenv("ABIS_AES_KEY_ALIAS");
        char[] password = System.getenv("ABIS_KEYSTORE_PASSWORD").toCharArray();

        try (FileInputStream fis = new FileInputStream(path)) {
            KeyStore keyStore = KeyStore.getInstance("JCEKS");
            keyStore.load(fis, password);
            SecretKey key = extraerLlave(keyStore, alias, password);
            limpiarPassword(password);
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("Error al cargar el KeyStore: " + e.getMessage(), e);
        }
    }

    /**
     * Extrae la entrada de clave secreta del KeyStore por su alias.
     *
     * @param keyStore KeyStore ya cargado
     * @param alias    alias de la clave AES
     * @param password contrasena para desproteger la entrada
     * @return clave secreta AES
     * @throws IllegalStateException si el alias no existe en el KeyStore
     * @throws Exception si falla el acceso a la entrada
     */
    private SecretKey extraerLlave(KeyStore keyStore, String alias, char[] password) throws Exception {
        KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry)
                keyStore.getEntry(alias, new KeyStore.PasswordProtection(password));
        if (entry == null)
            throw new IllegalStateException("No se encontro la clave con alias: " + alias);
        return entry.getSecretKey();
    }

    /**
     * Sobreescribe el arreglo de contrasena con ceros para limpiarla de memoria.
     *
     * @param password arreglo a limpiar
     */
    private void limpiarPassword(char[] password) {
        Arrays.fill(password, '\0');
    }

    /**
     * Retorna la clave AES cargada desde el KeyStore.
     *
     * <p>Usada por {@link CryptoService} para inicializar el cipher en cada
     * operacion de cifrado o descifrado.
     *
     * @return clave AES-256 en memoria
     */
    public SecretKey getAesKey() {
        return aesKey;
    }
}