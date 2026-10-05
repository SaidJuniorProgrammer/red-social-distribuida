package com.redsocial.controller;

import com.redsocial.dto.ApiErrorResponse;
import com.redsocial.dto.ApiMessageResponse;
import com.redsocial.dto.PushSubscriptionRequest;
import com.redsocial.service.PushEndpointValidator;
import com.redsocial.service.WebPushService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/push")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WebPushController {

    @Inject
    WebPushService webPushService;

    @Inject
    PushEndpointValidator pushEndpointValidator;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Path("/vapid-public-key")
    public Response obtenerVapidPublicKey() {
        return Response.ok(Map.of("publicKey", webPushService.getVapidPublicKey())).build();
    }

    @POST
    @Path("/subscribe")
    @Authenticated
    public Response suscribir(PushSubscriptionRequest request) {
        if (suscripcionInvalida(request)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiErrorResponse(
                            "INVALID_SUBSCRIPTION",
                            "endpoint",
                            "El endpoint y sus llaves son obligatorios."
                    ))
                    .build();
        }
        if (!pushEndpointValidator.esSeguro(request.endpoint())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiErrorResponse(
                            "INVALID_PUSH_ENDPOINT",
                            "endpoint",
                            "El endpoint de notificaciones no pertenece a un servicio Push permitido."
                    ))
                    .build();
        }

        PushSubscriptionRequest suscripcionAutenticada = new PushSubscriptionRequest(
                securityIdentity.getPrincipal().getName(),
                request.endpoint().trim(),
                request.keys()
        );
        webPushService.registrarSuscripcion(suscripcionAutenticada);
        return Response.status(Response.Status.CREATED)
                .entity(new ApiMessageResponse("Suscripción Web Push registrada exitosamente"))
                .build();
    }

    @DELETE
    @Path("/subscribe")
    @Authenticated
    public Response cancelarSuscripcion(@QueryParam("endpoint") String endpoint) {
        String usuario = securityIdentity.getPrincipal().getName();
        boolean eliminado = webPushService.eliminarSuscripcion(usuario, endpoint);
        if (!eliminado) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ApiErrorResponse("SUBSCRIPTION_NOT_FOUND", "usuario", "No existe suscripción activa para este usuario."))
                    .build();
        }
        return Response.ok(new ApiMessageResponse("Suscripción Web Push eliminada")).build();
    }

    @GET
    @Path("/notificaciones/{usuario}")
    @Authenticated
    public Response obtenerNotificaciones(@PathParam("usuario") String usuario) {
        if (!esUsuarioAutenticado(usuario)) {
            return accesoDenegado();
        }

        return Response.ok(Map.of("notificaciones", webPushService.obtenerNotificacionesDeUsuario(usuario))).build();
    }

    private boolean esUsuarioAutenticado(String usuario) {
        return usuario != null
                && usuario.trim().equals(securityIdentity.getPrincipal().getName());
    }

    private boolean suscripcionInvalida(PushSubscriptionRequest request) {
        if (request == null || request.endpoint() == null || request.endpoint().isBlank()
                || request.keys() == null) {
            return true;
        }

        String p256dh = request.keys().get("p256dh");
        String auth = request.keys().get("auth");
        return p256dh == null || p256dh.isBlank() || auth == null || auth.isBlank();
    }

    private Response accesoDenegado() {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiErrorResponse(
                        "FORBIDDEN",
                        "usuario",
                        "No puedes administrar las notificaciones de otra cuenta."
                ))
                .build();
    }
}
