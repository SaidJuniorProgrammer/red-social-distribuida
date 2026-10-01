package com.redsocial.controller;

import com.redsocial.dto.ApiErrorResponse;
import com.redsocial.dto.ApiMessageResponse;
import com.redsocial.dto.AuthTokenResponse;
import com.redsocial.dto.LoginRequest;
import com.redsocial.dto.RegisterRequest;
import com.redsocial.exception.InvalidCredentialsException;
import com.redsocial.exception.RegistrationConflictException;
import com.redsocial.exception.RegistrationValidationException;
import com.redsocial.service.AuthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthController {

    private static final Logger LOG = Logger.getLogger(AuthController.class);

    @Inject
    AuthService authService;

    @POST
    @Path("/register")
    public Response registrar(RegisterRequest request) {
        try {
            authService.registrarUsuario(request);

            return Response.status(Response.Status.CREATED)
                    .entity(new ApiMessageResponse("Usuario registrado con éxito"))
                    .build();
        } catch (RegistrationConflictException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ApiErrorResponse(e.getCode(), e.getField(), e.getMessage()))
                    .build();
        } catch (RegistrationValidationException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiErrorResponse("INVALID_REGISTRATION", e.getField(), e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.error("No se pudo registrar al usuario", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse(
                            "REGISTRATION_FAILED",
                            null,
                            "No pudimos crear la cuenta. Inténtalo nuevamente."
                    ))
                    .build();
        }
    }

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        try {
            String token = authService.login(request);
            return Response.ok(new AuthTokenResponse(token)).build();
        } catch (InvalidCredentialsException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ApiErrorResponse("INVALID_CREDENTIALS", null, e.getMessage()))
                    .build();
        } catch (Exception e) {
            LOG.error("No se pudo iniciar la sesión", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse(
                            "LOGIN_FAILED",
                            null,
                            "No pudimos iniciar sesión. Inténtalo nuevamente."
                    ))
                    .build();
        }
    }
}
