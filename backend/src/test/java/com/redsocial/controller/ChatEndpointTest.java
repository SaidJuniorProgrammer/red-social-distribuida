package com.redsocial.controller;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ChatEndpointTest {

    private static final LinkedBlockingDeque<String> MENSAJES_RECIBIDOS = new LinkedBlockingDeque<>();

    @TestHTTPResource("/chat/oscar")
    URI uriOscar;

    @TestHTTPResource("/chat/said")
    URI uriSaid;

    @Inject
    ChatEndpoint chatEndpoint;

    @ClientEndpoint
    public static class ClientePruebaSocket {
        @OnMessage
        public void onMessage(String msg) {
            MENSAJES_RECIBIDOS.add(msg);
        }
    }

    @Test
    void testChatTiempoRealYCasosBorde() throws Exception {
        MENSAJES_RECIBIDOS.clear();

        try (Session sesionOscar = ContainerProvider.getWebSocketContainer()
                .connectToServer(ClientePruebaSocket.class, uriOscar);
             Session sesionSaid = ContainerProvider.getWebSocketContainer()
                .connectToServer(ClientePruebaSocket.class, uriSaid)) {

            assertTrue(chatEndpoint.obtenerTotalSesionesActivas() >= 2);

            // 1. Envío de mensaje válido con todos los campos del contrato
            String jsonValido = """
                    {
                      "emisor_id": "said",
                      "destinatario_id": "oscar",
                      "contenido": "Hola Oscar, probando WebSocket!",
                      "timestamp": "2026-10-03T15:00:00Z"
                    }
                    """;
            sesionSaid.getAsyncRemote().sendText(jsonValido);

            String recibido1 = MENSAJES_RECIBIDOS.poll(5, TimeUnit.SECONDS);
            assertNotNull(recibido1);
            assertTrue(recibido1.contains("Hola Oscar, probando WebSocket!"));

            // 2. Envío sin emisor_id ni timestamp (se autocompletan en el servidor)
            String jsonAutocompletado = """
                    {
                      "destinatario_id": "oscar",
                      "contenido": "Segundo mensaje en tiempo real"
                    }
                    """;
            sesionSaid.getAsyncRemote().sendText(jsonAutocompletado);

            String recibido2 = MENSAJES_RECIBIDOS.poll(5, TimeUnit.SECONDS);
            assertNotNull(recibido2);
            assertTrue(recibido2.contains("Segundo mensaje en tiempo real"));
            assertTrue(recibido2.contains("said"));
        }

        // 3. Casos borde directos para cubrir validaciones y manejo de errores
        chatEndpoint.onOpen(null, "   ");
        chatEndpoint.onMessage("{\"destinatario_id\": \"\", \"contenido\": \"hola\"}", "said");
        chatEndpoint.onMessage("{\"destinatario_id\": \"oscar\", \"contenido\": \"   \"}", "said");
        chatEndpoint.onMessage("{\"destinatario_id\": \"usuario_desconectado\", \"contenido\": \"hola\"}", "said");
        chatEndpoint.onMessage("{json_malformado", "said");
        chatEndpoint.onError(null, "said", new RuntimeException("Error simulado de WebSocket"));
        chatEndpoint.onClose(null, null);

        assertEquals(0, chatEndpoint.obtenerTotalSesionesActivas());
    }
}