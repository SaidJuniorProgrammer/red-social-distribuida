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

@QuarkusTest
public class AuthIntegrationTest {

    @Inject
    UsuarioRepository usuarioRepository;

    private final Map<String, String> usuariosEnMemoria = new HashMap<>();

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
                    String username = params.get("username").asString();

                    if ("error_db".equals(username)) {
                        throw new RuntimeException("Fallo simulado de Neo4j");
                    }

                    if (query.contains("CREATE")) {
                        usuariosEnMemoria.put(username, params.get("password_hash").asString());
                        return null;
                    }

                    if (query.contains("MATCH")) {
                        String hash = usuariosEnMemoria.get(username);
                        return crearResultSimulado(hash);
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

    private Result crearResultSimulado(String hash) {
        Record recordProxy = (Record) Proxy.newProxyInstance(
            Record.class.getClassLoader(),
            new Class[]{Record.class},
            (proxy, method, args) -> {
                if ("get".equals(method.getName())) {
                    return Values.value(hash);
                }
                return null;
            }
        );

        return (Result) Proxy.newProxyInstance(
            Result.class.getClassLoader(),
            new Class[]{Result.class},
            (proxy, method, args) -> {
                if ("hasNext".equals(method.getName())) {
                    return hash != null;
                }
                if ("next".equals(method.getName())) {
                    return recordProxy;
                }
                return null;
            }
        );
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

        // 2. Login exitoso (200 y devuelve token)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + testUser + "\",\"password\":\"" + testPassword + "\"}")
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body(containsString("token"));

        // 3. Login fallido por contraseña incorrecta (401)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + testUser + "\",\"password\":\"clave_incorrecta\"}")
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401);

        // 4. Login fallido por usuario inexistente (401)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"no_existe\",\"password\":\"password123\"}")
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401);

        // 5. Registro fallido por error de base de datos (500)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"error_db\",\"email\":\"error@test.com\",\"password\":\"password123\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(500);
    }
}