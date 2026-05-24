package com.abisupc.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HashingServiceTest {

    static HashingService hashingService;

    @BeforeAll
    static void setup() {
        hashingService = new HashingService();
    }

    @Test
    void hashPassword_verificarPassword_coinciden() throws Exception {
        String password = "miPassword123";
        String hash = hashingService.hashPassword(password);
        assertTrue(hashingService.verificarPassword(password, hash));
    }

    @Test
    void hashAlterado_verificarPassword_retornaFalse() throws Exception {
        String password = "miPassword123";
        String hashAlterado = "0000000000000000000000000000000000000000000000000000000000000000";
        assertFalse(hashingService.verificarPassword(password, hashAlterado));
    }

    @Test
    void verificarIntegridad_templateOriginal_retornaTrue() throws Exception {
        byte[] template = "template biometrico".getBytes();
        String hash = hashingService.hashTemplate(template);
        assertTrue(hashingService.verificarIntegridad(template, hash));
    }

    @Test
    void verificarIntegridad_templateAlterado_retornaFalse() throws Exception {
        byte[] template = "template biometrico".getBytes();
        String hash = hashingService.hashTemplate(template);
        byte[] alterado = "template alterado".getBytes();
        assertFalse(hashingService.verificarIntegridad(alterado, hash));
    }
}