package com.redsocial.controller;

import com.redsocial.dto.RegisterRequest;
import com.redsocial.service.AuthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.redsocial.dto.LoginRequest;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthController {

    @Inject
    AuthService authService;

    @POST
    @Path("/register")
    public Response registrar(RegisterRequest request) {
        try {
            authService.registrarUsuario(request);
            
            return Response.status(Response.Status.CREATED)
                    .entity("{\"mensaje\": \"Usuario registrado con éxito\"}")
                    .build();
        } catch (Exception e) {
            
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Error al registrar usuario: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        try {
            String token = authService.login(request);
            return Response.ok("{\"token\": \"" + token + "\"}").build();
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
}