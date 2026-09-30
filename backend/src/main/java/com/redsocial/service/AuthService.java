package com.redsocial.service;

import com.redsocial.dto.RegisterRequest;
import com.redsocial.repository.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.mindrot.jbcrypt.BCrypt;
import java.util.UUID;
import com.redsocial.dto.LoginRequest;
import io.smallrye.jwt.build.Jwt;
import java.time.Duration;

@ApplicationScoped
public class AuthService {

    @Inject
    UsuarioRepository usuarioRepository;

    public void registrarUsuario(RegisterRequest request) {
        String id = UUID.randomUUID().toString();

        String passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt(12));

        usuarioRepository.crearUsuario(id, request.username, request.email, passwordHash);
    }

    public String login(LoginRequest request) {

        String hashGuardado = usuarioRepository.obtenerHashPorUsername(request.username);

        if (hashGuardado == null) {
            throw new RuntimeException("Usuario no encontrado");
        }

        if (!BCrypt.checkpw(request.password, hashGuardado)) {
            throw new RuntimeException("Credenciales inválidas");
        }

        return Jwt.issuer("https://redsocial.com/issuer")
                  .upn(request.username)
                  .groups("Usuario")
                  .expiresIn(Duration.ofHours(24))
                  .sign();
    }
}