package com.abisupc.security;

import javax.crypto.SecretKey;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Arrays;

public class KeyStoreManager {

    private static KeyStoreManager instance;
    private final SecretKey aesKey;

    private KeyStoreManager() {
        validarVariablesEntorno();
        aesKey = cargarKeyStore();
    }

    public static synchronized KeyStoreManager getInstance() {
        if (instance == null) {
            instance = new KeyStoreManager();
        }
        return instance;
    }

    private void validarVariablesEntorno() {
        if (System.getenv("ABIS_KEYSTORE_PATH") == null)
            throw new IllegalStateException("Variable de entorno ABIS_KEYSTORE_PATH no configurada");
        if (System.getenv("ABIS_KEYSTORE_PASSWORD") == null)
            throw new IllegalStateException("Variable de entorno ABIS_KEYSTORE_PASSWORD no configurada");
        if (System.getenv("ABIS_AES_KEY_ALIAS") == null)
            throw new IllegalStateException("Variable de entorno ABIS_AES_KEY_ALIAS no configurada");
    }

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

    private SecretKey extraerLlave(KeyStore keyStore, String alias, char[] password) throws Exception {
        KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry)
                keyStore.getEntry(alias, new KeyStore.PasswordProtection(password));
        if (entry == null)
            throw new IllegalStateException("No se encontro la clave con alias: " + alias);
        return entry.getSecretKey();
    }

    private void limpiarPassword(char[] password) {
        Arrays.fill(password, '\0');
    }

    public SecretKey getAesKey() {
        return aesKey;
    }
}