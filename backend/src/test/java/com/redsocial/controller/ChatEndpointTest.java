package com.redsocial.controller;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ChatEndpointTest {

    private static final LinkedBlockingDeque<String> MENSAJES_RECIBIDOS = new LinkedBlockingDeque<>();

    @TestHTTPResource("/chat")
    URI uriChat;

    @Inject
    ChatEndpoint chatEndpoint;

    static class ClientePruebaSocket extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.addMessageHandler(String.class, MENSAJES_RECIBIDOS::add);
        }
    }

    @Test
    void autenticaLaConexionYDerivaElEmisorDesdeElToken() throws Exception {
        MENSAJES_RECIBIDOS.clear();

        try (Session sesionOscar = conectarComo("oscar");
             Session sesionSaid = conectarComo("said")) {
            esperarSesionesActivas(2);

            String mensajeConEmisorFalso = """
                    {
                      "emisor_id": "mallory",
                      "destinatario_id": "oscar",
                      "contenido": "Hola Oscar, probando WebSocket!",
                      "timestamp": "2026-10-03T15:00:00Z"
                    }
                    """;
            sesionSaid.getAsyncRemote().sendText(mensajeConEmisorFalso);

            String recibido = MENSAJES_RECIBIDOS.poll(5, TimeUnit.SECONDS);
            assertNotNull(recibido);
            assertTrue(recibido.contains("Hola Oscar, probando WebSocket!"));
            assertTrue(recibido.contains("said"));
            assertFalse(recibido.contains("mallory"));

            sesionSaid.getAsyncRemote().sendText("""
                    {
                      "destinatario_id": "oscar",
                      "contenido": "Segundo mensaje en tiempo real"
                    }
                    """);

            String segundoRecibido = MENSAJES_RECIBIDOS.poll(5, TimeUnit.SECONDS);
            assertNotNull(segundoRecibido);
            assertTrue(segundoRecibido.contains("Segundo mensaje en tiempo real"));
            assertTrue(segundoRecibido.contains("said"));

            sesionSaid.getAsyncRemote().sendText(
                    "{\"destinatario_id\": \"\", \"contenido\": \"ignorado\"}"
            );
            assertEquals(0, MENSAJES_RECIBIDOS.size());
        }

        esperarSesionesActivas(0);
    }

    @Test
    void rechazaConexionesSinToken() {
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();

        assertThrows(IOException.class, () -> ContainerProvider.getWebSocketContainer()
                .connectToServer(new ClientePruebaSocket(), config, uriChat));
    }

    private Session conectarComo(String username) throws Exception {
        String token = Jwt.issuer("https://redsocial.com/issuer")
                .upn(username)
                .groups("Usuario")
                .expiresIn(Duration.ofMinutes(5))
                .sign();

        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .configurator(new ClientEndpointConfig.Configurator() {
                    @Override
                    public void beforeRequest(Map<String, List<String>> headers) {
                        headers.put("Authorization", List.of("Bearer " + token));
                    }
                })
                .build();

        return ContainerProvider.getWebSocketContainer()
                .connectToServer(new ClientePruebaSocket(), config, uriChat);
    }

    private void esperarSesionesActivas(int totalEsperado) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5000;
        while (chatEndpoint.obtenerTotalSesionesActivas() != totalEsperado
                && System.currentTimeMillis() < limite) {
            Thread.sleep(25);
        }
        assertEquals(totalEsperado, chatEndpoint.obtenerTotalSesionesActivas());
    }
}
