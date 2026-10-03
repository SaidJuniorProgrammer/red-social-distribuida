package com.redsocial.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redsocial.dto.ChatMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/chat/{username}")
@ApplicationScoped
public class ChatEndpoint {

    private static final Logger LOG = Logger.getLogger(ChatEndpoint.class);

    private final Map<String, Session> sesiones = new ConcurrentHashMap<>();

    @Inject
    ObjectMapper objectMapper;

    @OnOpen
    public void onOpen(Session session, @PathParam("username") String username) {
        if (username != null && !username.isBlank()) {
            sesiones.put(username.trim(), session);
            LOG.infof("Usuario conectado al chat: %s", username.trim());
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("username") String username) {
        if (username != null) {
            sesiones.remove(username.trim(), session);
            LOG.infof("Usuario desconectado del chat: %s", username.trim());
        }
    }

    @OnError
    public void onError(Session session, @PathParam("username") String username, Throwable throwable) {
        if (username != null) {
            sesiones.remove(username.trim(), session);
        }
        LOG.errorf(throwable, "Error en la sesión de chat del usuario: %s", username);
    }

    @OnMessage
    public void onMessage(String rawMessage, @PathParam("username") String username) {
        try {
            ChatMessage entrante = objectMapper.readValue(rawMessage, ChatMessage.class);

            if (entrante.destinatario_id() == null || entrante.destinatario_id().isBlank()
                    || entrante.contenido() == null || entrante.contenido().isBlank()) {
                return;
            }

            String emisor = (entrante.emisor_id() == null || entrante.emisor_id().isBlank())
                    ? username.trim()
                    : entrante.emisor_id().trim();

            String fecha = (entrante.timestamp() == null || entrante.timestamp().isBlank())
                    ? Instant.now().toString()
                    : entrante.timestamp().trim();

            ChatMessage mensajeNormalizado = new ChatMessage(
                    emisor,
                    entrante.destinatario_id().trim(),
                    entrante.contenido().trim(),
                    fecha
            );

            String payloadJson = objectMapper.writeValueAsString(mensajeNormalizado);

            Session sesionDestinatario = sesiones.get(mensajeNormalizado.destinatario_id());
            if (sesionDestinatario != null && sesionDestinatario.isOpen()) {
                sesionDestinatario.getAsyncRemote().sendText(payloadJson);
            }
        } catch (Exception e) {
            LOG.error("No se pudo procesar ni rutear el mensaje de chat", e);
        }
    }

    public int obtenerTotalSesionesActivas() {
        return sesiones.size();
    }
}