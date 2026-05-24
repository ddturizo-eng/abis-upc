package com.abisupc.security;

public interface ICryptoService {
    String cifrarTexto(String texto) throws Exception;
    String descifrarTexto(String textoCifrado) throws Exception;
}