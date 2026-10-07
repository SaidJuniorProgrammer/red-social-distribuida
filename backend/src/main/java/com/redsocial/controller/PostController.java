package com.redsocial.controller;

import com.redsocial.dto.ApiErrorResponse;
import com.redsocial.dto.GrafoStatusResponse;
import com.redsocial.dto.PostResponse;
import com.redsocial.repository.GrafoSocialRepository;
import com.redsocial.repository.PostRepository;
import com.redsocial.service.S3StorageService;
import com.redsocial.service.WebPushService;
import io.quarkus.security.Authenticated;
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

/**
 * Controlador REST para la creación de publicaciones multimedia y gestión de reacciones LIKE.
 */
@Path("/api/posts")
@Produces(MediaType.APPLICATION_JSON)
public class PostController {

    private static final Logger LOG = Logger.getLogger(PostController.class);
    private static final int MAX_TEXT_LENGTH = 500;
    private static final long MAX_MEDIA_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final String STATUS_SUCCESS = "success";

    @Inject
    S3StorageService s3StorageService;

    @Inject
    PostRepository postRepository;

    @Inject
    GrafoSocialRepository grafoSocialRepository;

    @Inject
    WebPushService webPushService;

    /**
     * Crea una nueva publicación subiendo su archivo multimedia a S3 y registrando el nodo en Neo4j.
     *
     * @param texto           contenido textual de la publicación
     * @param archivo         archivo multimedia opcional
     * @param securityContext contexto de seguridad de la petición
     * @return respuesta HTTP 201 con los datos de la publicación creada
     */
    @POST
    @Authenticated
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response crearPost(
            @RestForm("texto") String texto,
            @RestForm("archivo") FileUpload archivo,
            @Context SecurityContext securityContext
    ) {
        try {
            String textoNormalizado = texto == null ? "" : texto.trim();
            if (textoNormalizado.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse("INVALID_POST", "texto", "El texto de la publicación es obligatorio."))
                        .build();
            }

            if (textoNormalizado.length() > MAX_TEXT_LENGTH) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse(
                                "INVALID_POST",
                                "texto",
                                "La publicación no puede superar los 500 caracteres."
                        ))
                        .build();
            }

            String autor = obtenerAutor(null, securityContext);
            if (autor == null || autor.isBlank()) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(new ApiErrorResponse("UNAUTHORIZED", "autor", "Debes iniciar sesión para publicar."))
                        .build();
            }

            String idPost = UUID.randomUUID().toString();
            String fechaPublicacion = Instant.now().toString();
            String mediaUrl = null;
            String mediaTipo = null;

            if (archivo != null && archivo.uploadedFile() != null) {
                String contentType = archivo.contentType();
                boolean esImagen = contentType != null && contentType.startsWith("image/");
                boolean esVideo = contentType != null && contentType.startsWith("video/");
                if (!esImagen && !esVideo) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(new ApiErrorResponse(
                                    "INVALID_POST",
                                    "archivo",
                                    "El archivo debe ser una imagen o un video."
                            ))
                            .build();
                }

                if (archivo.size() > MAX_MEDIA_SIZE_BYTES) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(new ApiErrorResponse(
                                    "INVALID_POST",
                                    "archivo",
                                    "El archivo no puede superar los 10 MB."
                            ))
                            .build();
                }

                mediaTipo = esVideo ? "video" : "image";
                mediaUrl = s3StorageService.subirArchivo(
                        idPost,
                        archivo.fileName(),
                        contentType,
                        archivo.uploadedFile()
                );
            }

            boolean creado = postRepository.crearPost(
                    idPost,
                    autor.trim(),
                    textoNormalizado,
                    mediaUrl,
                    mediaTipo,
                    fechaPublicacion
            );

            if (!creado) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiErrorResponse("USER_NOT_FOUND", "autor", "El usuario autor no existe en Neo4j."))
                        .build();
            }

            webPushService.notificarSeguidoresNuevaPublicacion(autor.trim(), idPost, textoNormalizado);

            PostResponse response = new PostResponse(
                    idPost,
                    autor.trim(),
                    textoNormalizado,
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

    /**
     * Registra una reacción LIKE en una publicación y emite notificación Web Push si es una reacción nueva.
     *
     * @param idPost          identificador de la publicación
     * @param miId            identificador o username del usuario que reacciona
     * @param securityContext contexto de seguridad para validar la titularidad del usuario
     * @return respuesta HTTP con el resultado de la operación
     */
    @POST
    @Path("/{id_post}/like/{mi_id}")
    public Response darLikePost(
            @PathParam("id_post") String idPost,
            @PathParam("mi_id") String miId,
            @Context SecurityContext securityContext
    ) {
        try {
            if (esUsuarioNoAutorizado(miId, securityContext)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(new ApiErrorResponse("FORBIDDEN", "mi_id", "No puedes reaccionar en nombre de otro usuario."))
                        .build();
            }

            String usuarioEfectivo = obtenerAutor(miId.trim(), securityContext).trim();
            GrafoSocialRepository.LikeResult resultado = grafoSocialRepository.darLikePost(usuarioEfectivo, idPost.trim());
            if (resultado == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiErrorResponse("POST_OR_USER_NOT_FOUND", null, "La publicación o el usuario no existen."))
                        .build();
            }

            if (resultado.recienCreado()
                    && resultado.autor() != null
                    && !resultado.autor().isBlank()
                    && !resultado.autor().equalsIgnoreCase(usuarioEfectivo)) {
                webPushService.notificarLikePublicacion(resultado.autor(), usuarioEfectivo, idPost.trim());
            }

            return Response.ok(new GrafoStatusResponse(STATUS_SUCCESS, "Reacción LIKE registrada exitosamente")).build();
        } catch (Exception e) {
            LOG.error("Error al registrar like en la publicación", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiErrorResponse("LIKE_ERROR", null, "No se pudo registrar la reacción."))
                    .build();
        }
    }

    /**
     * Elimina una reacción LIKE de una publicación verificando la titularidad del usuario.
     *
     * @param idPost          identificador de la publicación
     * @param miId            identificador o username del usuario
     * @param securityContext contexto de seguridad para validar la titularidad del usuario
     * @return respuesta HTTP con el resultado de la eliminación
     */
    @DELETE
    @Path("/{id_post}/like/{mi_id}")
    public Response quitarLikePost(
            @PathParam("id_post") String idPost,
            @PathParam("mi_id") String miId,
            @Context SecurityContext securityContext
    ) {
        try {
            if (esUsuarioNoAutorizado(miId, securityContext)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(new ApiErrorResponse("FORBIDDEN", "mi_id", "No puedes eliminar reacciones de otro usuario."))
                        .build();
            }

            String usuarioEfectivo = obtenerAutor(miId.trim(), securityContext).trim();
            boolean eliminado = grafoSocialRepository.quitarLikePost(usuarioEfectivo, idPost.trim());
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

    /**
     * Verifica si existe un usuario autenticado distinto al indicado en la ruta.
     *
     * @param miId            identificador recibido en el path
     * @param securityContext contexto de seguridad activo
     * @return true si el principal autenticado no coincide con miId
     */
    private boolean esUsuarioNoAutorizado(String miId, SecurityContext securityContext) {
        String autenticado = obtenerAutor(null, securityContext);
        return autenticado != null && !autenticado.isBlank()
                && !autenticado.trim().equalsIgnoreCase(miId.trim());
    }

    /**
     * Obtiene el nombre del usuario autenticado desde el SecurityContext o usa el valor de respaldo.
     *
     * @param autorForm       valor de respaldo cuando no hay principal activo
     * @param securityContext contexto de seguridad de JAX-RS
     * @return nombre de usuario resuelto
     */
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
