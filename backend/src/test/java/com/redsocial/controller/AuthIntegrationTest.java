package com.redsocial.controller;

import com.redsocial.repository.UsuarioRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
public class AuthIntegrationTest {

    @Inject
    UsuarioRepository usuarioRepository;

    private final Map<String, TestUser> usuariosEnMemoria = new HashMap<>();

    @BeforeEach
    public void configurarDriverEnMemoria() {
        usuariosEnMemoria.clear();

        TransactionContext txProxy = (TransactionContext) Proxy.newProxyInstance(
            TransactionContext.class.getClassLoader(),
            new Class[]{TransactionContext.class},
            (proxy, method, args) -> {
                if ("run".equals(method.getName())) {
                    String query = (String) args[0];
                    Value params = (Value) args[1];

                    if (query.contains("count(u) > 0 AS exists")) {
                        String value = params.get("value").asString();

                        if ("error_db".equals(value)) {
                            throw new RuntimeException("Fallo simulado de Neo4j");
                        }

                        boolean exists = query.contains("username")
                                ? usuariosEnMemoria.containsKey(value)
                                : usuariosEnMemoria.values().stream()
                                        .anyMatch(user -> user.email().equals(value));

                        return crearResultSimulado("exists", Values.value(exists), true);
                    }

                    if (query.contains("CREATE")) {
                        String username = params.get("username").asString();
                        usuariosEnMemoria.put(
                                username,
                                new TestUser(
                                        params.get("email").asString(),
                                        params.get("password_hash").asString()
                                )
                        );
                        return null;
                    }

                    if (query.contains("password_hash")) {
                        TestUser user = usuariosEnMemoria.get(params.get("username").asString());
                        Value hash = user == null ? Values.NULL : Values.value(user.passwordHash());
                        return crearResultSimulado("hash", hash, user != null);
                    }
                }
                return null;
            }
        );

        Session sessionProxy = (Session) Proxy.newProxyInstance(
            Session.class.getClassLoader(),
            new Class[]{Session.class},
            (proxy, method, args) -> {
                if ("executeWrite".equals(method.getName()) || "executeRead".equals(method.getName())) {
                    TransactionCallback<?> callback = (TransactionCallback<?>) args[0];
                    return callback.execute(txProxy);
                }
                return null;
            }
        );

        Driver driverProxy = (Driver) Proxy.newProxyInstance(
            Driver.class.getClassLoader(),
            new Class[]{Driver.class},
            (proxy, method, args) -> {
                if ("session".equals(method.getName())) {
                    return sessionProxy;
                }
                return null;
            }
        );

        usuarioRepository.setDriver(driverProxy);
    }

    private Result crearResultSimulado(String key, Value value, boolean hasNext) {
        Record recordProxy = (Record) Proxy.newProxyInstance(
            Record.class.getClassLoader(),
                new Class[]{Record.class},
                (proxy, method, args) -> {
                if ("get".equals(method.getName()) && key.equals(args[0])) {
                    return value;
                }
                return null;
            }
        );

        return (Result) Proxy.newProxyInstance(
            Result.class.getClassLoader(),
                new Class[]{Result.class},
                (proxy, method, args) -> {
                if ("hasNext".equals(method.getName())) {
                    return hasNext;
                }
                if ("next".equals(method.getName())) {
                    return recordProxy;
                }
                return null;
            }
        );
    }

    private record TestUser(String email, String passwordHash) {
    }

    @Test
    public void testFlujoCompletoAutenticacion() {
        String testUser = "said_test";
        String testPassword = "password123";

        // 1. Registro exitoso (201)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + testUser + "\",\"email\":\"said@test.com\",\"password\":\"" + testPassword + "\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(201)
            .body(containsString("Usuario registrado con éxito"));

        // 2. El username debe ser único
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + testUser + "\",\"email\":\"otro@test.com\",\"password\":\"" + testPassword + "\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(409)
            .body("code", equalTo("USERNAME_ALREADY_EXISTS"))
            .body("field", equalTo("username"));

        // 3. El correo debe ser único
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"otro_usuario\",\"email\":\"said@test.com\",\"password\":\"" + testPassword + "\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(409)
            .body("code", equalTo("EMAIL_ALREADY_EXISTS"))
            .body("field", equalTo("email"));

        // 4. La contraseña debe tener al menos 8 caracteres
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"usuario_corto\",\"email\":\"corto@test.com\",\"password\":\"1234567\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(400)
            .body("code", equalTo("INVALID_REGISTRATION"))
            .body("field", equalTo("password"));

        // 5. Login exitoso (200 y devuelve token)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + testUser + "\",\"password\":\"" + testPassword + "\"}")
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body(containsString("token"));

        // 6. Login fallido por contraseña incorrecta (401)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + testUser + "\",\"password\":\"clave_incorrecta\"}")
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401)
            .body("code", equalTo("INVALID_CREDENTIALS"));

        // 7. Login fallido por usuario inexistente (401)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"no_existe\",\"password\":\"password123\"}")
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401)
            .body("code", equalTo("INVALID_CREDENTIALS"));

        // 8. Registro fallido por error de base de datos (500)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"error_db\",\"email\":\"error@test.com\",\"password\":\"password123\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(500)
            .body("code", equalTo("REGISTRATION_FAILED"));
    }
}
