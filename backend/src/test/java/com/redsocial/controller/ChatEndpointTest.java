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

    @TestHTTPResource("/chat")
    URI uriChat;

    @Inject
    ChatEndpoint chatEndpoint;

    static class ClientePruebaSocket extends Endpoint {
        private final LinkedBlockingDeque<String> mensajesRecibidos;

        ClientePruebaSocket(LinkedBlockingDeque<String> mensajesRecibidos) {
            this.mensajesRecibidos = mensajesRecibidos;
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.addMessageHandler(String.class, mensajesRecibidos::add);
        }
    }

    @Test
    void confirmaLaEntregaYDerivaElEmisorDesdeElToken() throws Exception {
        LinkedBlockingDeque<String> mensajesDeAdmin = new LinkedBlockingDeque<>();
        LinkedBlockingDeque<String> mensajesDeGualter = new LinkedBlockingDeque<>();

        try (Session sesionAdmin = conectarComo("admin", mensajesDeAdmin);
             Session sesionGualter = conectarComo("gualter", mensajesDeGualter)) {
            esperarSesionesActivas(2);

            String mensajeConEmisorFalso = """
                    {
                      "emisor_id": "mallory",
                      "destinatario_id": "admin",
                      "contenido": "Hola Admin, probando WebSocket!",
                      "timestamp": "2026-10-03T15:00:00Z"
                    }
                    """;
            sesionGualter.getAsyncRemote().sendText(mensajeConEmisorFalso);

            String recibidoPorAdmin = mensajesDeAdmin.poll(5, TimeUnit.SECONDS);
            assertNotNull(recibidoPorAdmin);
            assertTrue(recibidoPorAdmin.contains("Hola Admin, probando WebSocket!"));
            assertTrue(recibidoPorAdmin.contains("gualter"));
            assertFalse(recibidoPorAdmin.contains("mallory"));

            String confirmacionParaGualter = mensajesDeGualter.poll(5, TimeUnit.SECONDS);
            assertNotNull(confirmacionParaGualter);
            assertTrue(confirmacionParaGualter.contains("Hola Admin, probando WebSocket!"));

            sesionAdmin.close();
            esperarSesionesActivas(1);
            sesionGualter.getAsyncRemote().sendText("""
                    {
                      "destinatario_id": "admin",
                      "contenido": "Mensaje sin destinatario conectado"
                    }
                    """);

            String errorDeEntrega = mensajesDeGualter.poll(5, TimeUnit.SECONDS);
            assertNotNull(errorDeEntrega);
            assertTrue(errorDeEntrega.contains("delivery_error"));
            assertTrue(errorDeEntrega.contains("admin"));

            sesionGualter.getAsyncRemote().sendText(
                    "{\"destinatario_id\": \"\", \"contenido\": \"ignorado\"}"
            );
            assertEquals(0, mensajesDeGualter.size());
        }

        esperarSesionesActivas(0);
    }

    @Test
    void rechazaConexionesSinToken() {
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
        LinkedBlockingDeque<String> mensajes = new LinkedBlockingDeque<>();

        assertThrows(IOException.class, () -> ContainerProvider.getWebSocketContainer()
                .connectToServer(new ClientePruebaSocket(mensajes), config, uriChat));
    }

    @Test
    void reemplazaLaSesionAnteriorDelMismoUsuario() throws Exception {
        LinkedBlockingDeque<String> mensajes = new LinkedBlockingDeque<>();
        try (Session sesionAnterior = conectarComo("oscar", mensajes);
             Session sesionNueva = conectarComo("oscar", mensajes)) {
            esperarSesionesActivas(1);
            esperarSesionCerrada(sesionAnterior);
            assertTrue(sesionNueva.isOpen());
        }

        esperarSesionesActivas(0);
    }

    private Session conectarComo(
            String username,
            LinkedBlockingDeque<String> mensajesRecibidos
    ) throws Exception {
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
                .connectToServer(new ClientePruebaSocket(mensajesRecibidos), config, uriChat);
    }

    private void esperarSesionesActivas(int totalEsperado) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5000;
        while (chatEndpoint.obtenerTotalSesionesActivas() != totalEsperado
                && System.currentTimeMillis() < limite) {
            Thread.sleep(25);
        }
        assertEquals(totalEsperado, chatEndpoint.obtenerTotalSesionesActivas());
    }

    private void esperarSesionCerrada(Session session) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5000;
        while (session.isOpen() && System.currentTimeMillis() < limite) {
            Thread.sleep(25);
        }
        assertFalse(session.isOpen());
    }
}
