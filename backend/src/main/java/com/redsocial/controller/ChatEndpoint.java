package com.redsocial.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redsocial.dto.ChatDeliveryError;
import com.redsocial.dto.ChatHistory;
import com.redsocial.dto.ChatMessage;
import com.redsocial.repository.ChatRepository;
import com.redsocial.service.WebPushService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Authenticated
@WebSocket(path = "/chat")
@ApplicationScoped
public class ChatEndpoint {

    private static final Logger LOG = Logger.getLogger(ChatEndpoint.class);

    private final Map<String, WebSocketConnection> sesiones = new ConcurrentHashMap<>();

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    ChatRepository chatRepository;

    @Inject
    WebPushService webPushService;

    @OnOpen
    @Blocking
    public Uni<Void> onOpen(WebSocketConnection connection) {
        String username = usernameAutenticado();
        WebSocketConnection sesionAnterior = sesiones.put(username, connection);
        LOG.infof("Usuario conectado al chat: %s", username);

        Uni<Void> cerrarSesionAnterior = Uni.createFrom().voidItem();

        if (sesionAnterior != null && !sesionAnterior.equals(connection)) {
            cerrarSesionAnterior = sesionAnterior.close();
        }

        try {
            List<ChatMessage> historial = chatRepository.obtenerHistorial(username);
            String payload = objectMapper.writeValueAsString(
                    new ChatHistory("history", historial)
            );
            return cerrarSesionAnterior.chain(() -> connection.sendText(payload));
        } catch (Exception exception) {
            LOG.errorf(exception, "No se pudo cargar el historial de %s", username);
            return cerrarSesionAnterior;
        }
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        eliminarSesion(connection);
    }

    @OnError
    public void onError(Throwable throwable, WebSocketConnection connection) {
        eliminarSesion(connection);
        LOG.error("Error en una sesión autenticada del chat", throwable);
    }

    @OnTextMessage
    @Blocking
    public Uni<Void> onMessage(String rawMessage, WebSocketConnection connection) {
        try {
            ChatMessage entrante = objectMapper.readValue(rawMessage, ChatMessage.class);

            if (entrante.destinatario_id() == null || entrante.destinatario_id().isBlank()
                    || entrante.contenido() == null || entrante.contenido().isBlank()) {
                return Uni.createFrom().voidItem();
            }

            ChatMessage mensajeNormalizado = new ChatMessage(
                    UUID.randomUUID().toString(),
                    usernameAutenticado(),
                    entrante.destinatario_id().trim(),
                    entrante.contenido().trim(),
                    Instant.now().toString()
            );

            var mensajeGuardado = chatRepository.guardar(mensajeNormalizado);
            if (mensajeGuardado.isEmpty()) {
                return enviarErrorDeEntrega(
                        connection,
                        mensajeNormalizado.destinatario_id(),
                        "No se pudo enviar el mensaje porque el usuario no existe."
                );
            }

            ChatMessage mensaje = mensajeGuardado.get();

            // SOLUCIÓN CODERABBIT: Disparar la alerta Web Push del chat después del guardado exitoso
            webPushService.notificarNuevoMensaje(mensaje.emisor_id(), mensaje.destinatario_id(), mensaje.id());

            String payloadJson = objectMapper.writeValueAsString(mensaje);
            WebSocketConnection sesionDestinatario = sesiones.get(mensajeNormalizado.destinatario_id());
            if (sesionDestinatario == null || sesionDestinatario.equals(connection)) {
                return connection.sendText(payloadJson);
            }

            Uni<Void> entregaDestinatario = sesionDestinatario.sendText(payloadJson)
                    .onFailure()
                    .recoverWithUni(failure -> {
                        LOG.warnf(
                                "El mensaje quedó guardado para %s, pero no se pudo entregar en vivo: %s",
                                mensajeNormalizado.destinatario_id(),
                                failure.getMessage()
                        );
                        sesiones.remove(mensajeNormalizado.destinatario_id(), sesionDestinatario);
                        return Uni.createFrom().voidItem();
                    });
            return entregaDestinatario.chain(() -> connection.sendText(payloadJson));
        } catch (Exception exception) {
            LOG.error("No se pudo procesar ni rutear el mensaje de chat", exception);
            return enviarErrorDeEntrega(
                    connection,
                    "",
                    "No pudimos guardar el mensaje. Inténtalo nuevamente."
            );
        }
    }

    public int obtenerTotalSesionesActivas() {
        return sesiones.size();
    }

    private String usernameAutenticado() {
        return securityIdentity.getPrincipal().getName();
    }

    private Uni<Void> enviarErrorDeEntrega(
            WebSocketConnection connection,
            String destinatario,
            String message
    ) {
        try {
            String payload = objectMapper.writeValueAsString(new ChatDeliveryError(
                    "delivery_error",
                    destinatario,
                    message
            ));
            return connection.sendText(payload);
        } catch (Exception exception) {
            LOG.error("No se pudo informar el error de entrega", exception);
            return Uni.createFrom().voidItem();
        }
    }

    private void eliminarSesion(WebSocketConnection connection) {
        sesiones.entrySet().removeIf(entry -> entry.getValue().equals(connection));
    }
}