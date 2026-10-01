package com.redsocial.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;

@ApplicationScoped
public class UsuarioRepository {

    @Inject
    Driver driver;

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public void crearUsuario(String id, String username, String email, String passwordHash) {
        String query = """
            CREATE (u:Usuario {
                id: $id,
                username: $username,
                email: $email,
                password_hash: $password_hash
            })
            """;

        try (var session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(query, Values.parameters(
                    "id", id,
                    "username", username,
                    "email", email,
                    "password_hash", passwordHash
                ));
                return null;
            });
        }
    }

    public boolean existeUsername(String username) {
        return existePorCampo(
                "MATCH (u:Usuario {username: $value}) RETURN count(u) > 0 AS exists",
                username
        );
    }

    public boolean existeEmail(String email) {
        return existePorCampo(
                "MATCH (u:Usuario {email: $value}) RETURN count(u) > 0 AS exists",
                email
        );
    }

    public String obtenerHashPorUsername(String username) {
        String query = "MATCH (u:Usuario {username: $username}) RETURN u.password_hash AS hash";

        try (var session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(query, Values.parameters("username", username));
                if (result.hasNext()) {
                    return result.next().get("hash").asString();
                }
                return null;
            });
        }
    }

    private boolean existePorCampo(String query, String value) {
        try (var session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(query, Values.parameters("value", value));
                return result.hasNext() && result.next().get("exists").asBoolean();
            });
        }
    }
}
