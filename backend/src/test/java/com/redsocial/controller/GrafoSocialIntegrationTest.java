package com.redsocial.controller;

import com.redsocial.repository.GrafoSocialRepository;
import io.smallrye.jwt.build.Jwt;
import io.quarkus.test.junit.QuarkusTest;
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
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
class GrafoSocialIntegrationTest {

    @Inject
    GrafoSocialRepository grafoSocialRepository;

    @BeforeEach
    void configurarMocksEnMemoria() {
        Record feedRecord = (Record) Proxy.newProxyInstance(
                Record.class.getClassLoader(),
                new Class[]{Record.class},
                (proxy, method, args) -> {
                    if ("get".equals(method.getName())) {
                        String key = (String) args[0];
                        return switch (key) {
                            case "id_post" -> Values.value("p1");
                            case "autor" -> Values.value("carlos");
                            case "texto" -> Values.value("Mi primer post en la red social");
                            case "media_url" -> Values.value("http://localhost:9090/red-social-media/p1/foto.jpg");
                            case "fecha_publicacion" -> Values.value("2026-10-03T12:00:00Z");
                            case "reacciones" -> Values.value(5L);
                            case "recomendado" -> Values.value("said");
                            case "conexiones_en_comun" -> Values.value(2L);
                            default -> Values.value("ok");
                        };
                    }
                    return null;
                }
        );

        TransactionContext txProxy = (TransactionContext) Proxy.newProxyInstance(
                TransactionContext.class.getClassLoader(),
                new Class[]{TransactionContext.class},
                (proxy, method, args) -> {
                    if ("run".equals(method.getName())) {
                        Value params = (Value) args[1];
                        String miId = params.get("miId").asString("");

                        if ("error_db".equals(miId)) {
                            throw new RuntimeException("Fallo simulado en Neo4j");
                        }

                        boolean tieneResultado = !"no_existe".equals(miId);
                        AtomicInteger contador = new AtomicInteger(tieneResultado ? 1 : 0);

                        return (Result) Proxy.newProxyInstance(
                                Result.class.getClassLoader(),
                                new Class[]{Result.class},
                                (resProxy, resMethod, resArgs) -> {
                                    if ("hasNext".equals(resMethod.getName())) {
                                        return contador.getAndDecrement() > 0;
                                    }
                                    if ("next".equals(resMethod.getName())) {
                                        return feedRecord;
                                    }
                                    return null;
                                }
                        );
                    }
                    return null;
                }
        );

        Session sessionProxy = (Session) Proxy.newProxyInstance(
                Session.class.getClassLoader(),
                new Class[]{Session.class},
                (proxy, method, args) -> {
                    if ("executeRead".equals(method.getName()) || "executeWrite".equals(method.getName())) {
                        TransactionCallback<?> callback = (TransactionCallback<?>) args[0];
                        return callback.execute(txProxy);
                    }
                    return null;
                }
        );

        Driver driverProxy = (Driver) Proxy.newProxyInstance(
                Driver.class.getClassLoader(),
                new Class[]{Driver.class},
                (proxy, method, args) -> "session".equals(method.getName()) ? sessionProxy : null
        );

        grafoSocialRepository.setDriver(driverProxy);
    }

    @Test
    void testEndpointsGrafoSocial() {
        // 1. Obtener Feed exitoso (200)
        given()
        .when()
                .get("/api/feed/u1")
        .then()
                .statusCode(200)
                .body(containsString("Mi primer post en la red social"));

        // 2. Obtener Feed con error de BD (500)
        given()
        .when()
                .get("/api/feed/error_db")
        .then()
                .statusCode(500);

        // 3. Seguir usuario exitoso (200)
        given()
        .when()
                .post("/api/usuarios/u1/seguir/u2")
        .then()
                .statusCode(200)
                .body(containsString("Relación [:SIGUE] creada exitosamente"));

        // 4. Seguir a sí mismo (400)
        given()
        .when()
                .post("/api/usuarios/u1/seguir/u1")
        .then()
                .statusCode(400);

        // 5. Seguir usuario inexistente (404)
        given()
        .when()
                .post("/api/usuarios/no_existe/seguir/u2")
        .then()
                .statusCode(404);

        // 6. Seguir con fallo de BD (500)
        given()
        .when()
                .post("/api/usuarios/error_db/seguir/u2")
        .then()
                .statusCode(500);

        // 7. Dejar de seguir exitoso (200)
        given()
        .when()
                .delete("/api/usuarios/u1/seguir/u2")
        .then()
                .statusCode(200)
                .body(containsString("Relación [:SIGUE] eliminada exitosamente"));

        // 8. Dejar de seguir relación inexistente (404)
        given()
        .when()
                .delete("/api/usuarios/no_existe/seguir/u2")
        .then()
                .statusCode(404);

        // 9. Dejar de seguir con fallo de BD (500)
        given()
        .when()
                .delete("/api/usuarios/error_db/seguir/u2")
        .then()
                .statusCode(500);

        // 10. Obtener sugerencias de amistad a 2 niveles (200)
        given()
        .when()
                .get("/api/usuarios/u1/sugerencias")
        .then()
                .statusCode(200)
                .body(containsString("said"));

        // 11. Obtener sugerencias con fallo de BD (500)
        given()
        .when()
                .get("/api/usuarios/error_db/sugerencias")
        .then()
                .statusCode(500);
    }

    @Test
    void exigeUnaSesionParaBuscarUsuariosRegistrados() {
        given()
                .queryParam("query", "")
        .when()
                .get("/api/usuarios")
        .then()
                .statusCode(401);

        String token = Jwt.issuer("https://redsocial.com/issuer")
                .upn("oscar")
                .groups("Usuario")
                .expiresIn(Duration.ofMinutes(5))
                .sign();

        given()
                .auth().oauth2(token)
                .queryParam("query", "")
        .when()
                .get("/api/usuarios")
        .then()
                .statusCode(200)
                .body(containsString("usuarios"));
    }
}
