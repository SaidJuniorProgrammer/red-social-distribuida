package com.redsocial.controller;

import com.redsocial.dto.ChatMessage;
import com.redsocial.repository.ChatRepository;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ChatEndpointTest {

    @TestHTTPResource("/chat")
    URI uriChat;

    @Inject
    ChatEndpoint chatEndpoint;

    @Inject
    ChatRepository chatRepository;

    private final List<ChatMessage> mensajesGuardados = new ArrayList<>();

    @BeforeEach
    void configurarMensajesEnMemoria() {
        mensajesGuardados.clear();
        chatRepository.setDriver(crearDriverEnMemoria());
    }

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
    void guardaMensajesParaUsuariosDesconectadosYDerivaElEmisorDelToken() throws Exception {
        LinkedBlockingDeque<String> mensajesDeAdmin = new LinkedBlockingDeque<>();
        LinkedBlockingDeque<String> mensajesDeGualter = new LinkedBlockingDeque<>();

        try (Session sesionGualter = conectarComo("gualter", mensajesDeGualter)) {
            esperarSesionesActivas(1);
            assertTrue(recibir(mensajesDeGualter).contains("\"type\":\"history\""));

            String mensajeConEmisorFalso = """
                    {
                      "emisor_id": "mallory",
                      "destinatario_id": "admin",
                      "contenido": "Hola Admin, probando WebSocket!",
                      "timestamp": "2026-10-03T15:00:00Z"
                    }
                    """;
            sesionGualter.getAsyncRemote().sendText(mensajeConEmisorFalso);

            String confirmacionParaGualter = recibir(mensajesDeGualter);
            assertTrue(confirmacionParaGualter.contains("Hola Admin, probando WebSocket!"));
            assertTrue(confirmacionParaGualter.contains("gualter"));
            assertFalse(confirmacionParaGualter.contains("mallory"));
            assertEquals(1, mensajesGuardados.size());

            try (Session sesionAdmin = conectarComo("admin", mensajesDeAdmin)) {
                esperarSesionesActivas(2);
                String historialDeAdmin = recibir(mensajesDeAdmin);
                assertTrue(historialDeAdmin.contains("\"type\":\"history\""));
                assertTrue(historialDeAdmin.contains("Hola Admin, probando WebSocket!"));

                sesionGualter.getAsyncRemote().sendText("""
                        {
                          "destinatario_id": "admin",
                          "contenido": "Ahora estás conectado"
                        }
                        """);
                assertTrue(recibir(mensajesDeGualter).contains("Ahora estás conectado"));
                assertTrue(recibir(mensajesDeAdmin).contains("Ahora estás conectado"));
                assertTrue(sesionAdmin.isOpen());
            }

            sesionGualter.getAsyncRemote().sendText(
                    "{\"destinatario_id\": \"\", \"contenido\": \"ignorado\"}"
            );
            assertNull(mensajesDeGualter.poll(250, TimeUnit.MILLISECONDS));

            sesionGualter.getAsyncRemote().sendText(
                    "{\"destinatario_id\": \"fantasma\", \"contenido\": \"ignorado\"}"
            );
            String errorDeEntrega = recibir(mensajesDeGualter);
            assertTrue(errorDeEntrega.contains("delivery_error"));
            assertTrue(errorDeEntrega.contains("no existe"));
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
            assertTrue(recibir(mensajes).contains("\"type\":\"history\""));
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

    private String recibir(LinkedBlockingDeque<String> mensajes) throws InterruptedException {
        String mensaje = mensajes.poll(5, TimeUnit.SECONDS);
        assertNotNull(mensaje);
        return mensaje;
    }

    private Driver crearDriverEnMemoria() {
        Set<String> usuarios = Set.of("admin", "gualter", "oscar");

        TransactionContext transaction = (TransactionContext) Proxy.newProxyInstance(
                TransactionContext.class.getClassLoader(),
                new Class<?>[]{TransactionContext.class},
                (proxy, method, args) -> {
                    if (!"run".equals(method.getName())) {
                        return null;
                    }

                    String query = (String) args[0];
                    Value parameters = (Value) args[1];
                    List<Record> records = new ArrayList<>();

                    if (query.contains("CREATE (emisor)-[:ENVIA]")) {
                        String destinatario = parameters.get("destinatario").asString();
                        if (usuarios.contains(destinatario)) {
                            ChatMessage message = new ChatMessage(
                                    parameters.get("id").asString(),
                                    parameters.get("emisor").asString(),
                                    destinatario,
                                    parameters.get("contenido").asString(),
                                    parameters.get("timestamp").asString()
                            );
                            mensajesGuardados.add(message);
                            records.add(registroDe(message));
                        }
                    } else {
                        String username = parameters.get("username").asString();
                        mensajesGuardados.stream()
                                .filter(message -> username.equals(message.emisor_id())
                                        || username.equals(message.destinatario_id()))
                                .map(this::registroDe)
                                .forEach(records::add);
                    }

                    return resultadoDe(records);
                }
        );

        org.neo4j.driver.Session session = (org.neo4j.driver.Session) Proxy.newProxyInstance(
                org.neo4j.driver.Session.class.getClassLoader(),
                new Class<?>[]{org.neo4j.driver.Session.class},
                (proxy, method, args) -> {
                    if ("executeRead".equals(method.getName())
                            || "executeWrite".equals(method.getName())) {
                        TransactionCallback<?> callback = (TransactionCallback<?>) args[0];
                        return callback.execute(transaction);
                    }
                    return null;
                }
        );

        return (Driver) Proxy.newProxyInstance(
                Driver.class.getClassLoader(),
                new Class<?>[]{Driver.class},
                (proxy, method, args) -> "session".equals(method.getName()) ? session : null
        );
    }

    private Result resultadoDe(List<Record> records) {
        var index = new int[]{0};
        return (Result) Proxy.newProxyInstance(
                Result.class.getClassLoader(),
                new Class<?>[]{Result.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasNext" -> index[0] < records.size();
                    case "next" -> records.get(index[0]++);
                    case "list" -> mapearRegistros(records, args[0]);
                    default -> null;
                }
        );
    }

    @SuppressWarnings("unchecked")
    private List<Object> mapearRegistros(List<Record> records, Object mapper) {
        var function = (java.util.function.Function<Record, Object>) mapper;
        return records.stream().map(function).toList();
    }

    private Record registroDe(ChatMessage message) {
        Map<String, String> values = Map.of(
                "id", message.id(),
                "emisor", message.emisor_id(),
                "destinatario", message.destinatario_id(),
                "contenido", message.contenido(),
                "timestamp", message.timestamp()
        );
        return (Record) Proxy.newProxyInstance(
                Record.class.getClassLoader(),
                new Class<?>[]{Record.class},
                (proxy, method, args) -> "get".equals(method.getName())
                        ? Values.value(values.get((String) args[0]))
                        : null
        );
    }
}
