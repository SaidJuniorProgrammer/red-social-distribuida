package com.redsocial.dto;

public record ChatMessage(
        String emisor_id,
        String destinatario_id,
        String contenido,
        String timestamp
) {
}