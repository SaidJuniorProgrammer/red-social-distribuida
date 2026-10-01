package com.redsocial.service;

import com.redsocial.dto.RegisterRequest;
import com.redsocial.exception.InvalidCredentialsException;
import com.redsocial.exception.RegistrationConflictException;
import com.redsocial.exception.RegistrationValidationException;
import com.redsocial.repository.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.mindrot.jbcrypt.BCrypt;
import org.neo4j.driver.exceptions.ClientException;
import java.util.UUID;
import com.redsocial.dto.LoginRequest;
import io.smallrye.jwt.build.Jwt;
import java.time.Duration;
import java.util.Locale;

@ApplicationScoped
public class AuthService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String CONSTRAINT_VALIDATION_FAILED =
            "Neo.ClientError.Schema.ConstraintValidationFailed";

    @Inject
    UsuarioRepository usuarioRepository;

    public void registrarUsuario(RegisterRequest request) {
        if (request == null) {
            throw new RegistrationValidationException(null, "Completa los datos del registro.");
        }

        String username = normalizar(request.username);
        String email = normalizar(request.email).toLowerCase(Locale.ROOT);
        String password = request.password == null ? "" : request.password;

        validarRegistro(username, email, password);

        if (usuarioRepository.existeUsername(username)) {
            throw conflictoUsername();
        }

        if (usuarioRepository.existeEmail(email)) {
            throw conflictoEmail();
        }

        String id = UUID.randomUUID().toString();
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(10));

        try {
            usuarioRepository.crearUsuario(id, username, email, passwordHash);
        } catch (ClientException exception) {
            manejarConflictoConcurrente(exception, username, email);
        }
    }

    public String login(LoginRequest request) {

        if (request == null || request.username == null || request.password == null) {
            throw new InvalidCredentialsException();
        }

        String username = request.username.trim();

        String hashGuardado = usuarioRepository.obtenerHashPorUsername(username);

        if (hashGuardado == null || !BCrypt.checkpw(request.password, hashGuardado)) {
            throw new InvalidCredentialsException();
        }

        return Jwt.issuer("https://redsocial.com/issuer")
                  .upn(username)
                  .groups("Usuario")
                  .expiresIn(Duration.ofHours(24))
                  .sign();
    }

    private void validarRegistro(String username, String email, String password) {
        if (username.isEmpty()) {
            throw new RegistrationValidationException("username", "Ingresa un nombre de usuario.");
        }
        if (email.isEmpty()) {
            throw new RegistrationValidationException("email", "Ingresa tu correo electrónico.");
        }
        if (!emailValido(email)) {
            throw new RegistrationValidationException("email", "Ingresa un correo electrónico válido.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new RegistrationValidationException(
                    "password",
                    "La contraseña debe tener al menos 8 caracteres."
            );
        }
    }

    private String normalizar(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean emailValido(String email) {
        int atIndex = email.indexOf('@');
        int dotIndex = email.lastIndexOf('.');

        return atIndex > 0
                && atIndex == email.lastIndexOf('@')
                && dotIndex > atIndex + 1
                && dotIndex < email.length() - 1
                && email.chars().noneMatch(Character::isWhitespace);
    }

    private void manejarConflictoConcurrente(
            ClientException exception,
            String username,
            String email
    ) {
        if (!CONSTRAINT_VALIDATION_FAILED.equals(exception.code())) {
            throw exception;
        }

        if (usuarioRepository.existeUsername(username)) {
            throw conflictoUsername();
        }

        if (usuarioRepository.existeEmail(email)) {
            throw conflictoEmail();
        }

        throw exception;
    }

    private RegistrationConflictException conflictoUsername() {
        return new RegistrationConflictException(
                "USERNAME_ALREADY_EXISTS",
                "username",
                "Ese nombre de usuario ya está registrado."
        );
    }

    private RegistrationConflictException conflictoEmail() {
        return new RegistrationConflictException(
                "EMAIL_ALREADY_EXISTS",
                "email",
                "Ese correo electrónico ya está registrado."
        );
    }
}
