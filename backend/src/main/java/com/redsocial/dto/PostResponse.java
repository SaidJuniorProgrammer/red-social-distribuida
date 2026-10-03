package com.redsocial.dto;

public record PostResponse(
        String id_post,
        String autor,
        String texto,
        String media_url,
        String media_tipo,
        String fecha_publicacion,
        String message
) {
}