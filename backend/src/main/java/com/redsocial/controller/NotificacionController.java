package com.redsocial.controller;

import com.redsocial.repository.NotificacionRepository;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Controlador REST para gestionar la bandeja de notificaciones persistentes del usuario.
 */
@Path("/api/notificaciones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class NotificacionController {

    @Inject
    NotificacionRepository notificacionRepository;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    public Response listarNotificaciones() {
        String usuario = securityIdentity.getPrincipal().getName();
        var lista = notificacionRepository.listarNotificaciones(usuario);
        return Response.ok(Map.of("notificaciones", lista)).build();
    }

    @GET
    @Path("/no-leidas")
    public Response contarNoLeidas() {
        String usuario = securityIdentity.getPrincipal().getName();
        long cantidad = notificacionRepository.contarNoLeidas(usuario);
        return Response.ok(Map.of("no_leidas", cantidad)).build();
    }

    @PUT
    @Path("/{id}/leer")
    public Response marcarComoLeida(@PathParam("id") String id) {
        String usuario = securityIdentity.getPrincipal().getName();
        boolean actualizada = notificacionRepository.marcarComoLeida(id, usuario);
        if (actualizada) {
            return Response.ok(Map.of("status", "success")).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @PUT
    @Path("/leer-todas")
    public Response marcarTodasComoLeidas() {
        String usuario = securityIdentity.getPrincipal().getName();
        long actualizadas = notificacionRepository.marcarTodasComoLeidas(usuario);
        return Response.ok(Map.of("status", "success", "actualizadas", actualizadas)).build();
    }
}