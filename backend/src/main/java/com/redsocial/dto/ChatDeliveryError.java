package com.redsocial.dto;

public record ChatDeliveryError(
        String type,
        String destinatario_id,
        String message
) {
}
