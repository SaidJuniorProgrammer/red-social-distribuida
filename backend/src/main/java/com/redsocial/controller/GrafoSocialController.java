package com.redsocial.controller;

import com.redsocial.dto.ApiErrorResponse;
import com.redsocial.dto.FeedItemResponse;
import com.redsocial.dto.FeedResponse;
import com.redsocial.dto.GrafoStatusResponse;
import com.redsocial.dto.SugerenciaResponse;
import com.redsocial.repository.GrafoSocialRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class GrafoSocialController {

    private static final Logger LOG = Logger.getLogger(GrafoSocialController.class);

    @Inject
    GrafoSocialRepository grafoSocialRepository;

    @GET
    @Path("/feed/{mi_id}")
    public Response obtenerFeed(@PathParam("mi_id") String miId) {
        try {
            List<FeedItemResponse> feed = grafoSocialRepository.obtenerFeed(miId.trim());
            return Response.ok(new FeedResponse(feed)).build();
        } catch (Exception e) {
            LOG.error("Error al obtener el feed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("FEED_ERROR", null, "No se pudo cargar el feed."))
                    .build();
        }
    }

    @POST
    @Path("/usuarios/{mi_id}/seguir/{id_destino}")
    public Response seguirUsuario(
            @PathParam("mi_id") String miId,
            @PathParam("id_destino") String idDestino
    ) {
        try {
            if (miId.trim().equalsIgnoreCase(idDestino.trim())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse("INVALID_FOLLOW", "id_destino", "No puedes seguirte a ti mismo."))
                        .build();
            }

            boolean creado = grafoSocialRepository.seguirUsuario(miId.trim(), idDestino.trim());
            if (!creado) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiErrorResponse("USER_NOT_FOUND", null, "Uno de los usuarios no existe."))
                        .build();
            }

            return Response.ok(new GrafoStatusResponse("success", "Relación [:SIGUE] creada exitosamente")).build();
        } catch (Exception e) {
            LOG.error("Error al seguir usuario", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("FOLLOW_ERROR", null, "No se pudo crear la relación de seguimiento."))
                    .build();
        }
    }

    @DELETE
    @Path("/usuarios/{mi_id}/seguir/{id_destino}")
    public Response dejarDeSeguirUsuario(
            @PathParam("mi_id") String miId,
            @PathParam("id_destino") String idDestino
    ) {
        try {
            boolean eliminado = grafoSocialRepository.dejarDeSeguirUsuario(miId.trim(), idDestino.trim());
            if (!eliminado) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiErrorResponse("RELATION_NOT_FOUND", null, "No existía la relación de seguimiento."))
                        .build();
            }

            return Response.ok(new GrafoStatusResponse("success", "Relación [:SIGUE] eliminada exitosamente")).build();
        } catch (Exception e) {
            LOG.error("Error al dejar de seguir usuario", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("UNFOLLOW_ERROR", null, "No se pudo eliminar la relación de seguimiento."))
                    .build();
        }
    }

    @GET
    @Path("/usuarios/{mi_id}/sugerencias")
    public Response obtenerSugerencias(@PathParam("mi_id") String miId) {
        try {
            List<SugerenciaResponse> sugerencias = grafoSocialRepository.obtenerSugerencias(miId.trim());
            return Response.ok(Map.of("sugerencias", sugerencias)).build();
        } catch (Exception e) {
            LOG.error("Error al obtener sugerencias", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("SUGGESTIONS_ERROR", null, "No se pudieron obtener las sugerencias."))
                    .build();
        }
    }
}