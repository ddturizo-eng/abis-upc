package com.abisupc.service;

import com.abisupc.model.Administrador;
import com.abisupc.model.Sesion;
import com.abisupc.repository.AdministradorRepository;
import com.abisupc.repository.EleccionAdminRepository;
import com.abisupc.repository.SesionRepository;
import com.abisupc.repository.VotanteAdminRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

public class AdminService {

    private final AdministradorRepository adminRepo;
    private final SesionRepository sesionRepo;
    private final VotanteAdminRepository votanteAdminRepo;
    private final EleccionAdminRepository eleccionAdminRepo;

    public AdminService() {
        this(new AdministradorRepository(), new SesionRepository());
    }

    public AdminService(AdministradorRepository adminRepo, SesionRepository sesionRepo) {
        this.adminRepo = adminRepo;
        this.sesionRepo = sesionRepo;
        this.votanteAdminRepo = new VotanteAdminRepository();
        this.eleccionAdminRepo = new EleccionAdminRepository();
    }

    public LoginResult login(String usuario, String password) {
        if (usuario == null || usuario.isBlank() || password == null || password.isBlank()) {
            return new LoginResult(false, null, "Usuario y contraseña son requeridos");
        }

        Optional<Administrador> optAdmin = adminRepo.findByUsuario(usuario);
        if (optAdmin.isEmpty()) {
            return new LoginResult(false, null, "Usuario o contraseña incorrectos");
        }

        Administrador admin = optAdmin.get();
        String hashIngresado = sha256(password);

        if (!hashIngresado.equals(admin.getPasswordHash())) {
            return new LoginResult(false, null, "Usuario o contraseña incorrectos");
        }

        Optional<Sesion> sesionActiva = sesionRepo.findActivaByAdmin(admin.getId());
        if (sesionActiva.isPresent()) {
            return new LoginResult(true, sesionActiva.get().getToken(),
                    "Sesion ya activa. Token reutilizado.");
        }

        String token = UUID.randomUUID().toString();
        Sesion sesion = new Sesion();
        sesion.setToken(token);
        sesion.setIdAdministrador(admin.getId());
        sesionRepo.save(sesion);

        return new LoginResult(true, token, "Inicio de sesion exitoso");
    }

    public boolean logout(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Optional<Sesion> optSesion = sesionRepo.findByToken(token);
        if (optSesion.isEmpty()) {
            return false;
        }
        sesionRepo.invalidarToken(token);
        return true;
    }

    public Optional<Administrador> getAdminByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<Sesion> optSesion = sesionRepo.findByToken(token);
        if (optSesion.isEmpty()) {
            return Optional.empty();
        }
        return adminRepo.findById(optSesion.get().getIdAdministrador());
    }

    public void inhabilitarVotante(String identificacion, Long idAdmin, String motivo) throws SQLException {
        validarOperacionAdmin(identificacion, idAdmin, motivo);
        votanteAdminRepo.inhabilitarVotante(identificacion, idAdmin, motivo);
    }

    public void habilitarVotante(String identificacion, Long idAdmin, String motivo) throws SQLException {
        validarOperacionAdmin(identificacion, idAdmin, motivo);
        votanteAdminRepo.habilitarVotante(identificacion, idAdmin, motivo);
    }

    public void cerrarEleccion(Long idEleccion, Long idAdmin) throws SQLException {
        if (idEleccion == null || idEleccion <= 0) {
            throw new IllegalArgumentException("idEleccion requerido");
        }
        if (idAdmin == null || idAdmin <= 0) {
            throw new IllegalArgumentException("Administrador autenticado requerido");
        }
        eleccionAdminRepo.cerrarEleccion(idEleccion, idAdmin);
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }

    private void validarOperacionAdmin(String identificacion, Long idAdmin, String motivo) {
        if (identificacion == null || identificacion.isBlank()) {
            throw new IllegalArgumentException("identificacion requerida");
        }
        if (idAdmin == null || idAdmin <= 0) {
            throw new IllegalArgumentException("Administrador autenticado requerido");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("motivo requerido");
        }
    }

    public static class LoginResult {
        public final boolean success;
        public final String token;
        public final String message;

        public LoginResult(boolean success, String token, String message) {
            this.success = success;
            this.token = token;
            this.message = message;
        }
    }
}
