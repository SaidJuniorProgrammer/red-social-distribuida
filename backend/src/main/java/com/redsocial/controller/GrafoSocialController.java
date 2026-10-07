package com.redsocial.controller;

import com.redsocial.dto.ApiErrorResponse;
import com.redsocial.dto.FeedItemResponse;
import com.redsocial.dto.FeedResponse;
import com.redsocial.dto.GrafoStatusResponse;
import com.redsocial.dto.SugerenciaResponse;
import com.redsocial.dto.UsuarioResumenResponse;
import com.redsocial.repository.GrafoSocialRepository;
import com.redsocial.repository.UsuarioRepository;
import com.redsocial.service.WebPushService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para gestionar las consultas del grafo social, feed y perfil de usuario.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class GrafoSocialController {

    private static final Logger LOG = Logger.getLogger(GrafoSocialController.class);
    private static final String STATUS_SUCCESS = "success";

    @Inject
    GrafoSocialRepository grafoSocialRepository;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    WebPushService webPushService;

    @GET
    @Authenticated
    @Path("/usuarios")
    public Response buscarUsuarios(@QueryParam("query") String query) {
        String busqueda = query == null ? "" : query.trim();
        if (busqueda.isEmpty()) {
            return Response.ok(Map.of("usuarios", List.of())).build();
        }

        try {
            List<UsuarioResumenResponse> usuarios = usuarioRepository
                    .buscarUsernames(busqueda, securityIdentity.getPrincipal().getName())
                    .stream()
                    .map(UsuarioResumenResponse::new)
                    .toList();

            return Response.ok(Map.of("usuarios", usuarios)).build();
        } catch (Exception exception) {
            LOG.error("No se pudieron buscar usuarios registrados", exception);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse(
                            "USER_SEARCH_FAILED",
                            "query",
                            "No pudimos buscar usuarios en este momento."
                    ))
                    .build();
        }
    }

    @GET
    @Path("/feed/{mi_id}")
    public Response obtenerFeed(@PathParam("mi_id") String miId) {
        try {
            String lector = securityIdentity.isAnonymous() ? "" : securityIdentity.getPrincipal().getName();
            List<FeedItemResponse> feed = grafoSocialRepository.obtenerFeed(miId.trim(), lector);
            return Response.ok(new FeedResponse(feed)).build();
        } catch (Exception e) {
            LOG.error("Error al obtener el feed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("FEED_ERROR", null, "No se pudo cargar el feed."))
                    .build();
        }
    }

    @GET
    @Path("/usuarios/{mi_id}/posts")
    public Response obtenerPostsDeUsuario(@PathParam("mi_id") String miId) {
        try {
            String lector = securityIdentity.isAnonymous() ? "" : securityIdentity.getPrincipal().getName();
            List<FeedItemResponse> posts = grafoSocialRepository.obtenerPostsDeUsuario(miId.trim(), lector);
            return Response.ok(Map.of("posts", posts)).build();
        } catch (Exception e) {
            LOG.error("Error al obtener las publicaciones del usuario", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("USER_POSTS_ERROR", null, "No se pudieron obtener las publicaciones del perfil."))
                    .build();
        }
    }

    @GET
    @Path("/usuarios/{mi_id}/seguidores")
    public Response obtenerSeguidores(@PathParam("mi_id") String miId) {
        try {
            List<UsuarioResumenResponse> seguidores = grafoSocialRepository.obtenerSeguidores(miId.trim());
            return Response.ok(Map.of("seguidores", seguidores)).build();
        } catch (Exception e) {
            LOG.error("Error al obtener seguidores", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("FOLLOWERS_ERROR", null, "No se pudo obtener la lista de seguidores."))
                    .build();
        }
    }

    @GET
    @Path("/usuarios/{mi_id}/seguidos")
    public Response obtenerSeguidos(@PathParam("mi_id") String miId) {
        try {
            List<UsuarioResumenResponse> seguidos = grafoSocialRepository.obtenerSeguidos(miId.trim());
            return Response.ok(Map.of("seguidos", seguidos)).build();
        } catch (Exception e) {
            LOG.error("Error al obtener seguidos", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("FOLLOWING_ERROR", null, "No se pudo obtener la lista de seguidos."))
                    .build();
        }
    }

    @POST
    @Authenticated
    @Path("/usuarios/{mi_id}/seguir/{id_destino}")
    public Response seguirUsuario(
            @PathParam("mi_id") String miId,
            @PathParam("id_destino") String idDestino
    ) {
        try {
            if (!esCuentaAutenticada(miId)) return seguimientoDenegado();
            if (miId.trim().equalsIgnoreCase(idDestino.trim())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse("INVALID_FOLLOW", "id_destino", "No puedes seguirte a ti mismo."))
                        .build();
            }

            // SOLUCIÓN CODERABBIT: El repositorio ahora debe devolver el username en vez de un boolean
            String usernameDestino = grafoSocialRepository.seguirUsuario(miId.trim(), idDestino.trim());
            if (usernameDestino == null || usernameDestino.isBlank()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiErrorResponse("USER_NOT_FOUND", null, "Uno de los usuarios no existe."))
                        .build();
            }

            // Enviamos la notificación usando el Username válido devuelto por la base de datos
            webPushService.notificarNuevoSeguidor(miId.trim(), usernameDestino);

            return Response.ok(new GrafoStatusResponse(STATUS_SUCCESS, "Relación [:SIGUE] creada exitosamente")).build();
        } catch (Exception e) {
            LOG.error("Error al seguir usuario", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("FOLLOW_ERROR", null, "No se pudo crear la relación de seguimiento."))
                    .build();
        }
    }

    @DELETE
    @Authenticated
    @Path("/usuarios/{mi_id}/seguir/{id_destino}")
    public Response dejarDeSeguirUsuario(
            @PathParam("mi_id") String miId,
            @PathParam("id_destino") String idDestino
    ) {
        try {
            if (!esCuentaAutenticada(miId)) return seguimientoDenegado();
            boolean eliminado = grafoSocialRepository.dejarDeSeguirUsuario(miId.trim(), idDestino.trim());
            if (!eliminado) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiErrorResponse("RELATION_NOT_FOUND", null, "No existía la relación de seguimiento."))
                        .build();
            }

            return Response.ok(new GrafoStatusResponse(STATUS_SUCCESS, "Relación [:SIGUE] eliminada exitosamente")).build();
        } catch (Exception e) {
            LOG.error("Error al dejar de seguir usuario", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("UNFOLLOW_ERROR", null, "No se pudo eliminar la relación de seguimiento."))
                    .build();
        }
    }

    private boolean esCuentaAutenticada(String usuario) {
        return usuario.trim().equals(securityIdentity.getPrincipal().getName());
    }

    private Response seguimientoDenegado() {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiErrorResponse("FORBIDDEN", "mi_id", "No puedes modificar los seguimientos de otra cuenta."))
                .build();
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