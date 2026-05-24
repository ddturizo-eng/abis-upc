package com.abisupc.security;

public interface IHashingService {
    String hashPassword(String rawPassword) throws Exception;
    boolean verificarPassword(String rawPassword, String hashAlmacenado) throws Exception;
    boolean verificarIntegridad(byte[] template, String hashAlmacenado) throws Exception;
}