package com.redsocial.controller;

import com.redsocial.dto.ApiErrorResponse;
import com.redsocial.dto.ApiMessageResponse;
import com.redsocial.dto.PushSubscriptionRequest;
import com.redsocial.service.WebPushService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/push")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WebPushController {

    @Inject
    WebPushService webPushService;

    @GET
    @Path("/vapid-public-key")
    public Response obtenerVapidPublicKey() {
        return Response.ok(Map.of("publicKey", webPushService.getVapidPublicKey())).build();
    }

    @POST
    @Path("/subscribe")
    public Response suscribir(PushSubscriptionRequest request) {
        if (request == null || request.usuario() == null || request.usuario().isBlank()
                || request.endpoint() == null || request.endpoint().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiErrorResponse("INVALID_SUBSCRIPTION", "endpoint", "El usuario y el endpoint son obligatorios."))
                    .build();
        }

        webPushService.registrarSuscripcion(request);
        return Response.status(Response.Status.CREATED)
                .entity(new ApiMessageResponse("Suscripción Web Push registrada exitosamente"))
                .build();
    }

    @DELETE
    @Path("/subscribe/{usuario}")
    public Response cancelarSuscripcion(@PathParam("usuario") String usuario) {
        boolean eliminado = webPushService.eliminarSuscripcion(usuario);
        if (!eliminado) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ApiErrorResponse("SUBSCRIPTION_NOT_FOUND", "usuario", "No existe suscripción activa para este usuario."))
                    .build();
        }
        return Response.ok(new ApiMessageResponse("Suscripción Web Push eliminada")).build();
    }

    @GET
    @Path("/notificaciones/{usuario}")
    public Response obtenerNotificaciones(@PathParam("usuario") String usuario) {
        return Response.ok(Map.of("notificaciones", webPushService.obtenerNotificacionesDeUsuario(usuario))).build();
    }
}