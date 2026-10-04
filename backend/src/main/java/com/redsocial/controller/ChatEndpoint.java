package com.redsocial.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redsocial.dto.ChatMessage;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
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

    @OnOpen
    public Uni<Void> onOpen(WebSocketConnection connection) {
        String username = usernameAutenticado();
        WebSocketConnection sesionAnterior = sesiones.put(username, connection);
        LOG.infof("Usuario conectado al chat: %s", username);

        if (sesionAnterior != null && !sesionAnterior.equals(connection)) {
            return sesionAnterior.close();
        }

        return Uni.createFrom().voidItem();
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
    public Uni<Void> onMessage(String rawMessage) {
        try {
            ChatMessage entrante = objectMapper.readValue(rawMessage, ChatMessage.class);

            if (entrante.destinatario_id() == null || entrante.destinatario_id().isBlank()
                    || entrante.contenido() == null || entrante.contenido().isBlank()) {
                return Uni.createFrom().voidItem();
            }

            String fecha = (entrante.timestamp() == null || entrante.timestamp().isBlank())
                    ? Instant.now().toString()
                    : entrante.timestamp().trim();

            ChatMessage mensajeNormalizado = new ChatMessage(
                    usernameAutenticado(),
                    entrante.destinatario_id().trim(),
                    entrante.contenido().trim(),
                    fecha
            );

            WebSocketConnection sesionDestinatario = sesiones.get(mensajeNormalizado.destinatario_id());
            if (sesionDestinatario == null) {
                return Uni.createFrom().voidItem();
            }

            String payloadJson = objectMapper.writeValueAsString(mensajeNormalizado);
            return sesionDestinatario.sendText(payloadJson);
        } catch (Exception exception) {
            LOG.error("No se pudo procesar ni rutear el mensaje de chat", exception);
            return Uni.createFrom().voidItem();
        }
    }

    public int obtenerTotalSesionesActivas() {
        return sesiones.size();
    }

    private String usernameAutenticado() {
        return securityIdentity.getPrincipal().getName();
    }

    private void eliminarSesion(WebSocketConnection connection) {
        sesiones.entrySet().removeIf(entry -> entry.getValue().equals(connection));
    }
}
