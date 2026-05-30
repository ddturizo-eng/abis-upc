package com.abisupc.model;

import java.time.LocalDateTime;

public class TokenContingencia {
/**
 * Representa un token de contingencia emitido para un votante que no puede
 * autenticarse biometricamente durante una jornada electoral.
 *
 * <p>El flujo de contingencia genera un token de un solo uso con tiempo de
 * expiracion. {@code tokenHash} es el valor hasheado almacenado en Oracle;
 * {@code tokenValor} es el valor en texto plano que se envia al votante
 * y nunca se persiste. {@code tokenHint} es un fragmento visible para
 * ayudar al votante a identificar su token sin exponer el valor completo.
 *
 * <p>Un token puede estar en estado {@code PENDIENTE}, {@code USADO} o
 * {@code EXPIRADO}. {@code fechaUso} e {@code idPuestoUso} se registran
 * al momento de redimir el token en el kiosco.
 * Tabla Oracle: {@code TOKENS_CONTINGENCIA}.
 */
public class TokenContingencia {

    private Long idToken;
    private String identificacion;
    private Long idEleccion;
    private String tokenHash;
    private String tokenHint;
    private String tokenValor;
    private String estado;
    private LocalDateTime fechaEmision;
    private LocalDateTime fechaExpiracion;
    private LocalDateTime fechaUso;
    private Long idPuestoUso;
    private String scannerId;

    public Long getIdToken() {
        return idToken;
    }
    public void setIdToken(Long idToken) {
        this.idToken = idToken;
    }

    public String getIdentificacion() {
        return identificacion;
    }
    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
    }

    public Long getIdEleccion() {
        return idEleccion;
    }
    public void setIdEleccion(Long idEleccion) {
        this.idEleccion = idEleccion;
    }

    public String getTokenHash() {
        return tokenHash;
    }
    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getTokenHint() {
        return tokenHint;
    }
    public void setTokenHint(String tokenHint) {
        this.tokenHint = tokenHint;
    }

    public String getTokenValor() {
        return tokenValor;
    }
    public void setTokenValor(String tokenValor) {
        this.tokenValor = tokenValor;
    }

    public String getEstado() {
        return estado;
    }
    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaEmision() {
        return fechaEmision;
    }
    public void setFechaEmision(LocalDateTime fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    public LocalDateTime getFechaExpiracion() {
        return fechaExpiracion;
    }
    public void setFechaExpiracion(LocalDateTime fechaExpiracion) {
        this.fechaExpiracion = fechaExpiracion;
    }

    public LocalDateTime getFechaUso() {
        return fechaUso;
    }
    public void setFechaUso(LocalDateTime fechaUso) {
        this.fechaUso = fechaUso;
    }

    public Long getIdPuestoUso() {
        return idPuestoUso;
    }
    public void setIdPuestoUso(Long idPuestoUso) {
        this.idPuestoUso = idPuestoUso;
    }

    public String getScannerId() {
        return scannerId;
    }
    public void setScannerId(String scannerId) {
        this.scannerId = scannerId;
    }
}
    public void setScannerId(String scannerId) {
        this.scannerId = scannerId;
    }
}
