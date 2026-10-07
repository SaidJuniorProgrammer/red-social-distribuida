package com.redsocial.dto;

/**
 * DTO que representa una notificación persistente enviada al frontend.
 *
 * @param idNotificacion identificador único de la notificación (ej. LIKE_carlos_post1)
 * @param tipo           tipo de evento (ej. LIKE, FOLLOW, POST, MENSAJE)
 * @param actor          usuario que originó el evento
 * @param destinatario   usuario que recibe la notificación
 * @param mensaje        texto descriptivo de la alerta
 * @param referencia     URL interna o ID (ej. /feed, /perfil/carlos)
 * @param fecha          fecha y hora en formato ISO-8601
 * @param leida          estado de lectura de la notificación
 */
public record NotificacionResponse(
        String idNotificacion,
        String tipo,
        String actor,
        String destinatario,
        String mensaje,
        String referencia,
        String fecha,
        boolean leida
) {
}