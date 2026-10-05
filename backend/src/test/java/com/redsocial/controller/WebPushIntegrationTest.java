package com.redsocial.controller;

import com.redsocial.dto.PushSubscriptionRequest;
import com.redsocial.repository.PostRepository;
import com.redsocial.service.S3StorageService;
import com.redsocial.service.WebPushService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class WebPushIntegrationTest {

    @Inject
    PostRepository postRepository;

    @Inject
    S3StorageService s3StorageService;

    @Inject
    WebPushService webPushService;

    @BeforeEach
    void configurarMocks() {
        S3Client s3Proxy = (S3Client) Proxy.newProxyInstance(
                S3Client.class.getClassLoader(),
                new Class[]{S3Client.class},
                (proxy, method, args) -> {
                    if ("headBucket".equals(method.getName())) {
                        return HeadBucketResponse.builder().build();
                    }
                    if ("putObject".equals(method.getName())) {
                        return PutObjectResponse.builder().build();
                    }
                    return null;
                }
        );
        s3StorageService.setS3Client(s3Proxy);

        List<String> seguidoresMock = List.of("anthony", "estalin");
        AtomicInteger indiceSeguidor = new AtomicInteger(0);

        TransactionContext txProxy = (TransactionContext) Proxy.newProxyInstance(
                TransactionContext.class.getClassLoader(),
                new Class[]{TransactionContext.class},
                (proxy, method, args) -> {
                    if ("run".equals(method.getName())) {
                        String query = (String) args[0];
                        if (query.contains("CREATE (u)-[:PUBLICA]->(p:Post")) {
                            Record postRecord = (Record) Proxy.newProxyInstance(
                                    Record.class.getClassLoader(),
                                    new Class[]{Record.class},
                                    (rProxy, rMethod, rArgs) -> Values.value("post-push-1")
                            );
                            return (Result) Proxy.newProxyInstance(
                                    Result.class.getClassLoader(),
                                    new Class[]{Result.class},
                                    (resProxy, resMethod, resArgs) -> {
                                        if ("hasNext".equals(resMethod.getName())) return true;
                                        if ("next".equals(resMethod.getName())) return postRecord;
                                        return null;
                                    }
                            );
                        }

                        indiceSeguidor.set(0);
                        return (Result) Proxy.newProxyInstance(
                                Result.class.getClassLoader(),
                                new Class[]{Result.class},
                                (resProxy, resMethod, resArgs) -> {
                                    if ("hasNext".equals(resMethod.getName())) {
                                        return indiceSeguidor.get() < seguidoresMock.size();
                                    }
                                    if ("next".equals(resMethod.getName())) {
                                        String nombreSeguidor = seguidoresMock.get(indiceSeguidor.getAndIncrement());
                                        return (Record) Proxy.newProxyInstance(
                                                Record.class.getClassLoader(),
                                                new Class[]{Record.class},
                                                (rProxy, rMethod, rArgs) -> Values.value(nombreSeguidor)
                                        );
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
                (proxy, method, args) -> "session".equals(method.getName()) ? sessionProxy : null
        );

        postRepository.setDriver(driverProxy);
    }

    @Test
    void testFlujoCompletoWebPushYVapid() throws Exception {
        String tokenAnthony = tokenPara("anthony");
        String tokenEstalin = tokenPara("estalin");

        // 1. Verificar generación de llaves VAPID
        assertNotNull(webPushService.getVapidPublicKey());
        assertNotNull(webPushService.getVapidPrivateKey());
        assertFalse(webPushService.getVapidPublicKey().isBlank());

        given()
        .when()
                .get("/api/push/vapid-public-key")
        .then()
                .statusCode(200)
                .body("publicKey", notNullValue());

        // 2. Suscribir seguidor 'anthony' con endpoint Web Push (201)
        PushSubscriptionRequest subAnthony = new PushSubscriptionRequest(
                "usuario-manipulado",
                "https://fcm.googleapis.com/fcm/send/demo-anthony",
                Map.of("p256dh", "llave-p256dh", "auth", "llave-auth")
        );

        given()
                .header("Authorization", "Bearer " + tokenAnthony)
                .contentType("application/json")
                .body(subAnthony)
        .when()
                .post("/api/push/subscribe")
        .then()
                .statusCode(201)
                .body(containsString("Suscripción Web Push registrada exitosamente"));

        // 3. Validaciones de suscripción inválida (400)
        given()
                .header("Authorization", "Bearer " + tokenAnthony)
                .contentType("application/json")
        .when()
                .post("/api/push/subscribe")
        .then()
                .statusCode(400);

        given()
                .header("Authorization", "Bearer " + tokenAnthony)
                .contentType("application/json")
                .body("""
                        {
                          "endpoint": "https://endpoint",
                          "keys": {"p256dh": null, "auth": "llave-auth"}
                        }
                        """)
        .when()
                .post("/api/push/subscribe")
        .then()
                .statusCode(400);

        given()
                .header("Authorization", "Bearer " + tokenAnthony)
                .contentType("application/json")
                .body("""
                        {
                          "endpoint": "https://endpoint",
                          "keys": {"p256dh": "llave-p256dh", "auth": null}
                        }
                        """)
        .when()
                .post("/api/push/subscribe")
        .then()
                .statusCode(400);

        given()
                .header("Authorization", "Bearer " + tokenAnthony)
                .contentType("application/json")
                .body(new PushSubscriptionRequest(
                        null,
                        "https://endpoint",
                        Map.of("p256dh", "", "auth", "")
                ))
        .when()
                .post("/api/push/subscribe")
        .then()
                .statusCode(400);

        given()
                .header("Authorization", "Bearer " + tokenAnthony)
                .contentType("application/json")
                .body(new PushSubscriptionRequest(
                        null,
                        null,
                        Map.of("p256dh", "llave-p256dh", "auth", "llave-auth")
                ))
        .when()
                .post("/api/push/subscribe")
        .then()
                .statusCode(400);

        given()
                .header("Authorization", "Bearer " + tokenAnthony)
                .contentType("application/json")
                .body(new PushSubscriptionRequest(
                        null,
                        "   ",
                        Map.of("p256dh", "llave-p256dh", "auth", "llave-auth")
                ))
        .when()
                .post("/api/push/subscribe")
        .then()
                .statusCode(400);

        given()
                .contentType("application/json")
                .body(subAnthony)
        .when()
                .post("/api/push/subscribe")
        .then()
                .statusCode(401);

        PushSubscriptionRequest subEstalin = new PushSubscriptionRequest(
                null,
                subAnthony.endpoint(),
                subAnthony.keys()
        );
        given()
                .header("Authorization", "Bearer " + tokenEstalin)
                .contentType("application/json")
                .body(subEstalin)
        .when()
                .post("/api/push/subscribe")
        .then()
                .statusCode(201);

        // 4. Crear nueva publicación de 'carlos' -> busca seguidores en Neo4j y emite Push a 'anthony' y 'estalin'
        File tempFile = Files.createTempFile("push_post", ".jpg").toFile();
        Files.writeString(tempFile.toPath(), "imagen-push");
        tempFile.deleteOnExit();

        given()
                .multiPart("texto", "Publicacion que dispara Web Push")
                .multiPart("autor", "carlos")
                .multiPart("archivo", tempFile, "image/jpeg")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(201);

        // 5. Verificar que 'anthony' (con suscripción) y 'estalin' (sin suscripción previa) recibieron el evento Push
        given()
                .header("Authorization", "Bearer " + tokenAnthony)
        .when()
                .get("/api/push/notificaciones/anthony")
        .then()
                .statusCode(200)
                .body(containsString("Publicacion que dispara Web Push"))
                .body(containsString("webpush://pendiente/anthony"));

        given()
                .header("Authorization", "Bearer " + tokenEstalin)
        .when()
                .get("/api/push/notificaciones/estalin")
        .then()
                .statusCode(200)
                .body(containsString("Publicacion que dispara Web Push"))
                .body(containsString(subAnthony.endpoint()));

        // 6. Eliminar suscripción existente (200) e inexistente (404)
        given()
                .header("Authorization", "Bearer " + tokenEstalin)
        .when()
                .delete("/api/push/subscribe/estalin")
        .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + tokenEstalin)
        .when()
                .delete("/api/push/subscribe/estalin")
        .then()
                .statusCode(404);

        given()
                .header("Authorization", "Bearer " + tokenAnthony)
        .when()
                .get("/api/push/notificaciones/estalin")
        .then()
                .statusCode(403);
    }

    private String tokenPara(String username) {
        return Jwt.issuer("https://redsocial.com/issuer")
                .upn(username)
                .groups("Usuario")
                .expiresIn(Duration.ofMinutes(5))
                .sign();
    }
}
