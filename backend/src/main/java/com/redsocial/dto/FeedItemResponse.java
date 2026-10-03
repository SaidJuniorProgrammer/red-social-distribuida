package com.redsocial.dto;

public record FeedItemResponse(
        String id_post,
        String autor,
        String texto,
        String media_url,
        String fecha_publicacion,
        long reacciones
) {
}