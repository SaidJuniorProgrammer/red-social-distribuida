package com.redsocial.dto;

public record PushNotificationPayload(
        String id_post,
        String autor,
        String seguidor,
        String titulo,
        String mensaje,
        String endpoint_destino,
        String timestamp
) {
}