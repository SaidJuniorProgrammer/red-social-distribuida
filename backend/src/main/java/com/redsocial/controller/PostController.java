package com.redsocial.controller;

import com.redsocial.dto.ApiErrorResponse;
import com.redsocial.dto.GrafoStatusResponse;
import com.redsocial.dto.PostResponse;
import com.redsocial.repository.GrafoSocialRepository;
import com.redsocial.repository.PostRepository;
import com.redsocial.service.S3StorageService;
import com.redsocial.service.WebPushService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.Instant;
import java.util.UUID;

@Path("/api/posts")
@Produces(MediaType.APPLICATION_JSON)
public class PostController {

    private static final Logger LOG = Logger.getLogger(PostController.class);
    private static final String STATUS_SUCCESS = "success";

    @Inject
    S3StorageService s3StorageService;

    @Inject
    PostRepository postRepository;

    @Inject
    GrafoSocialRepository grafoSocialRepository;

    @Inject
    WebPushService webPushService;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response crearPost(
            @RestForm("texto") String texto,
            @RestForm("autor") String autorForm,
            @RestForm("archivo") FileUpload archivo,
            @Context SecurityContext securityContext
    ) {
        try {
            if (texto == null || texto.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse("INVALID_POST", "texto", "El texto de la publicación es obligatorio."))
                        .build();
            }

            if (archivo == null || archivo.uploadedFile() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse("INVALID_POST", "archivo", "El archivo multimedia es obligatorio."))
                        .build();
            }

            String autor = obtenerAutor(autorForm, securityContext);
            if (autor == null || autor.isBlank()) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(new ApiErrorResponse("UNAUTHORIZED", "autor", "Debes iniciar sesión o indicar el autor."))
                        .build();
            }

            String idPost = UUID.randomUUID().toString();
            String fechaPublicacion = Instant.now().toString();
            String contentType = archivo.contentType();
            String mediaTipo = (contentType != null && contentType.startsWith("video")) ? "video" : "image";

            String mediaUrl = s3StorageService.subirArchivo(
                    idPost,
                    archivo.fileName(),
                    contentType,
                    archivo.uploadedFile()
            );

            boolean creado = postRepository.crearPost(
                    idPost,
                    autor.trim(),
                    texto.trim(),
                    mediaUrl,
                    mediaTipo,
                    fechaPublicacion
            );

            if (!creado) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiErrorResponse("USER_NOT_FOUND", "autor", "El usuario autor no existe en Neo4j."))
                        .build();
            }

            webPushService.notificarSeguidoresNuevaPublicacion(autor.trim(), idPost, texto.trim());

            PostResponse response = new PostResponse(
                    idPost,
                    autor.trim(),
                    texto.trim(),
                    mediaUrl,
                    mediaTipo,
                    fechaPublicacion,
                    "Publicación creada con éxito"
            );

            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (Exception e) {
            LOG.error("Error al crear la publicación", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("POST_CREATION_FAILED", null, "No se pudo crear la publicación."))
                    .build();
        }
    }

    @POST
    @Path("/{id_post}/like/{mi_id}")
    public Response darLikePost(
            @PathParam("id_post") String idPost,
            @PathParam("mi_id") String miId
    ) {
        try {
            String autorPost = grafoSocialRepository.darLikePost(miId.trim(), idPost.trim());
            if (autorPost == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiErrorResponse("POST_OR_USER_NOT_FOUND", null, "La publicación o el usuario no existen."))
                        .build();
            }

            if (!autorPost.equalsIgnoreCase(miId.trim())) {
                webPushService.notificarLikePublicacion(autorPost, miId.trim(), idPost.trim());
            }

            return Response.ok(new GrafoStatusResponse(STATUS_SUCCESS, "Reacción LIKE registrada exitosamente")).build();
        } catch (Exception e) {
            LOG.error("Error al registrar like en la publicación", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("LIKE_ERROR", null, "No se pudo registrar la reacción."))
                    .build();
        }
    }

    @DELETE
    @Path("/{id_post}/like/{mi_id}")
    public Response quitarLikePost(
            @PathParam("id_post") String idPost,
            @PathParam("mi_id") String miId
    ) {
        try {
            boolean eliminado = grafoSocialRepository.quitarLikePost(miId.trim(), idPost.trim());
            if (!eliminado) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiErrorResponse("LIKE_NOT_FOUND", null, "No existía una reacción previa en esta publicación."))
                        .build();
            }

            return Response.ok(new GrafoStatusResponse(STATUS_SUCCESS, "Reacción LIKE eliminada exitosamente")).build();
        } catch (Exception e) {
            LOG.error("Error al eliminar like de la publicación", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("UNLIKE_ERROR", null, "No se pudo eliminar la reacción."))
                    .build();
        }
    }

    private String obtenerAutor(String autorForm, SecurityContext securityContext) {
        if (securityContext != null && securityContext.getUserPrincipal() != null) {
            String principalName = securityContext.getUserPrincipal().getName();
            if (principalName != null && !principalName.isBlank()) {
                return principalName;
            }
        }
        return autorForm;
    }
}