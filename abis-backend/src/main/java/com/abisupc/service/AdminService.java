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

/**
 * Servicio de autenticacion y operaciones administrativas del sistema electoral.
 *
 * <p>Gestiona el ciclo de vida de las sesiones de administrador: login, logout
 * y recuperacion del admin autenticado por token. Tambien delega operaciones
 * sensibles como inhabilitar/habilitar votantes y cerrar elecciones a los
 * repositorios especializados, validando los parametros antes de ejecutar.
 *
 * <p>Las contrasenas se comparan usando SHA-256 en formato hexadecimal.
 * Si el administrador ya tiene una sesion activa al hacer login, se reutiliza
 * el token existente en lugar de crear uno nuevo.
 */
public class AdminService {

    private final AdministradorRepository adminRepo;
    private final SesionRepository sesionRepo;
    private final VotanteAdminRepository votanteAdminRepo;
    private final EleccionAdminRepository eleccionAdminRepo;

    /** Constructor por defecto. Crea sus propios repositorios. */
    public AdminService() {
        this(new AdministradorRepository(), new SesionRepository());
    }

    /**
     * Constructor para inyeccion de dependencias (util en pruebas).
     *
     * @param adminRepo  repositorio de administradores
     * @param sesionRepo repositorio de sesiones
     */
    public AdminService(AdministradorRepository adminRepo, SesionRepository sesionRepo) {
        this.adminRepo = adminRepo;
        this.sesionRepo = sesionRepo;
        this.votanteAdminRepo = new VotanteAdminRepository();
        this.eleccionAdminRepo = new EleccionAdminRepository();
    }

    /**
     * Autentica un administrador y retorna un token de sesion.
     *
     * <p>Si el administrador ya tiene una sesion activa, reutiliza el token
     * existente. Si las credenciales son invalidas, retorna un resultado
     * fallido con mensaje generico para no revelar si el usuario existe.
     *
     * @param usuario  nombre de usuario del administrador
     * @param password contrasena en texto plano
     * @return resultado del login con {@code success}, {@code token} y {@code message}
     */
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

    /**
     * Invalida el token de sesion del administrador (logout).
     *
     * @param token token de sesion a invalidar
     * @return {@code true} si el token existia y fue invalidado
     */
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

    /**
     * Recupera el administrador asociado a un token de sesion activo.
     *
     * @param token token de sesion
     * @return {@link Optional} con el administrador si el token es valido
     */
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

    /**
     * Inhabilita un votante registrando el motivo y el administrador responsable.
     *
     * @param identificacion cedula del votante a inhabilitar
     * @param idAdmin        ID del administrador que ejecuta la accion
     * @param motivo         justificacion de la inhabilitacion
     * @throws SQLException             si falla el acceso a la base de datos
     * @throws IllegalArgumentException si alguno de los parametros es nulo o vacio
     */
    public void inhabilitarVotante(String identificacion, Long idAdmin, String motivo) throws SQLException {
        validarOperacionAdmin(identificacion, idAdmin, motivo);
        votanteAdminRepo.inhabilitarVotante(identificacion, idAdmin, motivo);
    }

    /**
     * Habilita un votante previamente inhabilitado.
     *
     * @param identificacion cedula del votante a habilitar
     * @param idAdmin        ID del administrador que ejecuta la accion
     * @param motivo         justificacion de la habilitacion
     * @throws SQLException             si falla el acceso a la base de datos
     * @throws IllegalArgumentException si alguno de los parametros es nulo o vacio
     */
    public void habilitarVotante(String identificacion, Long idAdmin, String motivo) throws SQLException {
        validarOperacionAdmin(identificacion, idAdmin, motivo);
        votanteAdminRepo.habilitarVotante(identificacion, idAdmin, motivo);
    }

    /**
     * Cierra una eleccion en curso por accion explicita del administrador.
     *
     * @param idEleccion ID de la eleccion a cerrar
     * @param idAdmin    ID del administrador que ejecuta el cierre
     * @throws SQLException             si falla el acceso a la base de datos
     * @throws IllegalArgumentException si los IDs son nulos o invalidos
     */
    public void cerrarEleccion(Long idEleccion, Long idAdmin) throws SQLException {
        if (idEleccion == null || idEleccion <= 0) {
            throw new IllegalArgumentException("idEleccion requerido");
        }
        if (idAdmin == null || idAdmin <= 0) {
            throw new IllegalArgumentException("Administrador autenticado requerido");
        }
        eleccionAdminRepo.cerrarEleccion(idEleccion, idAdmin);
    }

    /**
     * Calcula el hash SHA-256 de una cadena en formato hexadecimal.
     *
     * <p>Se usa para comparar la contrasena ingresada con el hash almacenado
     * en {@code ADMINISTRADORES.PASSWORD_HASH}.
     *
     * @param input cadena a hashear
     * @return hash SHA-256 en hexadecimal (64 caracteres)
     * @throws RuntimeException si SHA-256 no esta disponible en la JVM
     */
    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }

    /**
     * Valida que los tres parametros requeridos para operaciones admin no sean nulos ni vacios.
     *
     * @param identificacion cedula del votante
     * @param idAdmin        ID del administrador
     * @param motivo         justificacion de la operacion
     * @throws IllegalArgumentException si alguno es nulo o vacio
     */
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

    /**
     * Resultado inmutable del intento de login de un administrador.
     */
    public static class LoginResult {

        /** Indica si el login fue exitoso. */
        public final boolean success;

        /** Token de sesion generado o reutilizado; {@code null} si el login fallo. */
        public final String token;

        /** Mensaje descriptivo del resultado. */
        public final String message;

        public LoginResult(boolean success, String token, String message) {
            this.success = success;
            this.token = token;
            this.message = message;
        }
    }
}
