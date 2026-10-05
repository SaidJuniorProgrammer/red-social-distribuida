package com.redsocial.repository;

import com.redsocial.dto.PushSubscriptionRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PushSubscriptionRepository {

    @Inject
    Driver driver;

    public void guardar(PushSubscriptionRequest subscription) {
        String query = """
                MATCH (usuario:Usuario {username: $usuario})
                WHERE usuario.password_hash IS NOT NULL
                MERGE (suscripcion:PushSubscription {endpoint: $endpoint})
                SET suscripcion.p256dh = $p256dh,
                    suscripcion.auth = $auth
                WITH usuario, suscripcion
                OPTIONAL MATCH (otro:Usuario)-[anterior:RECIBE_PUSH]->(suscripcion)
                WHERE otro <> usuario
                DELETE anterior
                MERGE (usuario)-[:RECIBE_PUSH]->(suscripcion)
                """;

        try (var session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(query, Values.parameters(
                        "usuario", subscription.usuario(),
                        "endpoint", subscription.endpoint(),
                        "p256dh", subscription.keys().get("p256dh"),
                        "auth", subscription.keys().get("auth")
                ));
                return null;
            });
        }
    }

    public boolean eliminar(String usuario, String endpoint) {
        String query = """
                MATCH (:Usuario {username: $usuario})-[relacion:RECIBE_PUSH]
                      ->(suscripcion:PushSubscription {endpoint: $endpoint})
                WITH relacion, suscripcion, count(suscripcion) AS eliminadas
                DELETE relacion, suscripcion
                RETURN eliminadas
                """;

        try (var session = driver.session()) {
            return session.executeWrite(tx -> {
                var result = tx.run(query, Values.parameters(
                        "usuario", usuario,
                        "endpoint", endpoint
                ));
                return result.hasNext() && result.next().get("eliminadas").asLong() > 0;
            });
        }
    }

    public List<PushSubscriptionRequest> obtenerTodas() {
        String query = """
                MATCH (usuario:Usuario)-[:RECIBE_PUSH]->(suscripcion:PushSubscription)
                WHERE usuario.password_hash IS NOT NULL
                RETURN usuario.username AS usuario,
                       suscripcion.endpoint AS endpoint,
                       suscripcion.p256dh AS p256dh,
                       suscripcion.auth AS auth
                """;

        try (var session = driver.session()) {
            return session.executeRead(tx -> tx.run(query).list(record -> new PushSubscriptionRequest(
                    record.get("usuario").asString(),
                    record.get("endpoint").asString(),
                    Map.of(
                            "p256dh", record.get("p256dh").asString(),
                            "auth", record.get("auth").asString()
                    )
            )));
        }
    }
}
