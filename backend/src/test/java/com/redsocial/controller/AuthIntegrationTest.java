package com.redsocial.controller;

import com.redsocial.repository.UsuarioRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
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
import org.neo4j.driver.exceptions.ClientException;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class AuthIntegrationTest {

    private static final String CONSTRAINT_VALIDATION_FAILED =
            "Neo.ClientError.Schema.ConstraintValidationFailed";
    private static final String CONCURRENT_USERNAME = "usuario_concurrente";
    private static final String CONCURRENT_EMAIL = "concurrente@test.com";

    @Inject
    UsuarioRepository usuarioRepository;

    private final Map<String, TestUser> usuariosEnMemoria = new HashMap<>();
    private int consultasUsernameConcurrente;
    private int consultasEmailConcurrente;

    @BeforeEach
    public void configurarDriverEnMemoria() {
        usuariosEnMemoria.clear();
        consultasUsernameConcurrente = 0;
        consultasEmailConcurrente = 0;

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

                        boolean exists;
                        if (query.contains("username") && CONCURRENT_USERNAME.equals(value)) {
                            consultasUsernameConcurrente++;
                            exists = consultasUsernameConcurrente > 1;
                        } else if (query.contains("email") && CONCURRENT_EMAIL.equals(value)) {
                            consultasEmailConcurrente++;
                            exists = consultasEmailConcurrente > 1;
                        } else {
                            exists = query.contains("username")
                                    ? usuariosEnMemoria.containsKey(value)
                                    : usuariosEnMemoria.values().stream()
                                            .anyMatch(user -> user.email().equals(value));
                        }

                        return crearResultSimulado("exists", Values.value(exists), true);
                    }

                    if (query.contains("CREATE")) {
                        String username = params.get("username").asString();
                        String email = params.get("email").asString();

                        if (CONCURRENT_USERNAME.equals(username) || CONCURRENT_EMAIL.equals(email)) {
                            throw new ClientException(
                                    CONSTRAINT_VALIDATION_FAILED,
                                    "Conflicto de unicidad simulado"
                            );
                        }

                        usuariosEnMemoria.put(
                                username,
                                new TestUser(
                                        email,
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

        // 5. El correo debe tener una estructura válida
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"correo_invalido\",\"email\":\"usuario@@test.com\",\"password\":\"" + testPassword + "\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(400)
            .body("code", equalTo("INVALID_REGISTRATION"))
            .body("field", equalTo("email"));

        // 6. Login exitoso (200 y devuelve token)
        String token = given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + testUser + "\",\"password\":\"" + testPassword + "\"}")
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body(containsString("token"))
            .extract()
            .path("token");

        String tokenPayload = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                StandardCharsets.UTF_8
        );
        JsonPath claims = JsonPath.from(tokenPayload);
        assertEquals(600L, claims.getLong("exp") - claims.getLong("iat"));

        // 7. Login fallido por contraseña incorrecta (401)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + testUser + "\",\"password\":\"clave_incorrecta\"}")
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401)
            .body("code", equalTo("INVALID_CREDENTIALS"));

        // 8. Login fallido por usuario inexistente (401)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"no_existe\",\"password\":\"password123\"}")
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401)
            .body("code", equalTo("INVALID_CREDENTIALS"));

        // 9. Registro fallido por error de base de datos (500)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"error_db\",\"email\":\"error@test.com\",\"password\":\"password123\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(500)
            .body("code", equalTo("REGISTRATION_FAILED"));

        // 10. Un conflicto concurrente de username también debe responder 409
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + CONCURRENT_USERNAME
                    + "\",\"email\":\"otro-concurrente@test.com\",\"password\":\"password123\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(409)
            .body("code", equalTo("USERNAME_ALREADY_EXISTS"))
            .body("field", equalTo("username"));

        // 11. Un conflicto concurrente de email también debe responder 409
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"usuario_email_concurrente\",\"email\":\""
                    + CONCURRENT_EMAIL + "\",\"password\":\"password123\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(409)
            .body("code", equalTo("EMAIL_ALREADY_EXISTS"))
            .body("field", equalTo("email"));
    }
}
