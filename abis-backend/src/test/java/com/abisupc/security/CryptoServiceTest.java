package com.abisupc.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.crypto.AEADBadTagException;
import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    static CryptoService cryptoService;

    @BeforeAll
    static void setup() {
        cryptoService = new CryptoService();
    }

    @Test
    void cifrarDescifrar_retornaTextoOriginal() throws Exception {
        String texto = "texto de prueba";
        String cifrado = cryptoService.cifrarTexto(texto);
        String resultado = cryptoService.descifrarTexto(cifrado);
        assertEquals(texto, resultado);
    }

    @Test
    void descifrarConIVAlterado_lanzaAEADBadTagException() throws Exception {
        String texto = "texto de prueba";
        String cifrado = cryptoService.cifrarTexto(texto);
        byte[] datos = java.util.Base64.getDecoder().decode(cifrado);
        datos[0] = (byte) (datos[0] ^ 0xFF);
        String alterado = java.util.Base64.getEncoder().encodeToString(datos);
        assertThrows(AEADBadTagException.class, () -> cryptoService.descifrarTexto(alterado));
    }

    @Test
    void dosLlamadasCifrar_generanIVsDiferentes() throws Exception {
        String texto = "texto de prueba";
        String cifrado1 = cryptoService.cifrarTexto(texto);
        String cifrado2 = cryptoService.cifrarTexto(texto);
        assertNotEquals(cifrado1, cifrado2);
    }
}