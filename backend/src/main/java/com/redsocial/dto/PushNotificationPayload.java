package com.redsocial.dto;

/**
 * Payload enviado al Service Worker del navegador para las notificaciones Web Push.
 *
 * @param idNotificacion identificador único del evento
 * @param tipo           tipo de notificación (POST, LIKE, FOLLOW, MENSAJE)
 * @param actor          usuario que originó la interacción
 * @param destinatario   usuario receptor
 * @param titulo         título visible en la alerta
 * @param mensaje        texto del cuerpo de la alerta
 * @param referencia     URL interna para navegar al hacer clic (ej. /feed, /perfil/juan)
 * @param fecha          marca de tiempo ISO-8601
 */
public record PushNotificationPayload(
        String idNotificacion,
        String tipo,
        String actor,
        String destinatario,
        String titulo,
        String mensaje,
        String referencia,
        String fecha
) {
}