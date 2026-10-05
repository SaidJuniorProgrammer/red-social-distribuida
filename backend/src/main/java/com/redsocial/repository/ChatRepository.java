package com.redsocial.repository;

import com.redsocial.dto.ChatMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ChatRepository {

    @Inject
    Driver driver;

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public Optional<ChatMessage> guardar(ChatMessage message) {
        String query = """
                MATCH (emisor:Usuario {username: $emisor}),
                      (destinatario:Usuario {username: $destinatario})
                WHERE emisor.password_hash IS NOT NULL
                  AND destinatario.password_hash IS NOT NULL
                CREATE (emisor)-[:ENVIA]->(mensaje:Mensaje {
                    id: $id,
                    contenido: $contenido,
                    timestamp: datetime($timestamp)
                })-[:DIRIGIDO_A]->(destinatario)
                RETURN mensaje.id AS id,
                       emisor.username AS emisor,
                       destinatario.username AS destinatario,
                       mensaje.contenido AS contenido,
                       toString(mensaje.timestamp) AS timestamp
                """;

        try (var session = driver.session()) {
            return session.executeWrite(tx -> {
                var result = tx.run(query, Values.parameters(
                        "id", message.id(),
                        "emisor", message.emisor_id(),
                        "destinatario", message.destinatario_id(),
                        "contenido", message.contenido(),
                        "timestamp", message.timestamp()
                ));
                return result.hasNext()
                        ? Optional.of(mapear(result.next()))
                        : Optional.empty();
            });
        }
    }

    public List<ChatMessage> obtenerHistorial(String username) {
        String query = """
                MATCH (emisor:Usuario)-[:ENVIA]->(mensaje:Mensaje)-[:DIRIGIDO_A]->(destinatario:Usuario)
                WHERE emisor.username = $username OR destinatario.username = $username
                RETURN mensaje.id AS id,
                       emisor.username AS emisor,
                       destinatario.username AS destinatario,
                       mensaje.contenido AS contenido,
                       toString(mensaje.timestamp) AS timestamp
                ORDER BY mensaje.timestamp ASC
                LIMIT 500
                """;

        try (var session = driver.session()) {
            return session.executeRead(tx -> tx.run(
                    query,
                    Values.parameters("username", username)
            ).list(this::mapear));
        }
    }

    private ChatMessage mapear(org.neo4j.driver.Record record) {
        return new ChatMessage(
                record.get("id").asString(),
                record.get("emisor").asString(),
                record.get("destinatario").asString(),
                record.get("contenido").asString(),
                record.get("timestamp").asString()
        );
    }
}
