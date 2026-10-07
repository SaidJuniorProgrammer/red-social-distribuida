package com.redsocial.controller;

import com.redsocial.dto.PushSubscriptionRequest;
import com.redsocial.repository.PostRepository;
import com.redsocial.repository.PushSubscriptionRepository;
import com.redsocial.service.S3StorageService;
import io.smallrye.jwt.build.Jwt;
import io.quarkus.test.junit.QuarkusMock;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
class PostIntegrationTest {

    @Inject
    PostRepository postRepository;

    @Inject
    S3StorageService s3StorageService;

    private final AtomicBoolean bucketExiste = new AtomicBoolean(false);

    private String token(String username) {
        return Jwt.issuer("https://redsocial.com/issuer")
                .upn(username)
                .groups("Usuario")
                .expiresIn(Duration.ofMinutes(5))
                .sign();
    }

    @BeforeEach
    void configurarMocksEnMemoria() {
        bucketExiste.set(false);
        QuarkusMock.installMockForType(new PushSubscriptionRepository() {
            @Override
            public List<PushSubscriptionRequest> obtenerTodas() {
                return List.of();
            }
        }, PushSubscriptionRepository.class);

        S3Client s3Proxy = (S3Client) Proxy.newProxyInstance(
                S3Client.class.getClassLoader(),
                new Class[]{S3Client.class},
                (proxy, method, args) -> {
                    if ("headBucket".equals(method.getName())) {
                        if (!bucketExiste.get()) {
                            throw NoSuchBucketException.builder().message("Bucket no existe").build();
                        }
                        return HeadBucketResponse.builder().build();
                    }
                    if ("createBucket".equals(method.getName())) {
                        bucketExiste.set(true);
                        return CreateBucketResponse.builder().build();
                    }
                    if ("putObject".equals(method.getName())) {
                        return PutObjectResponse.builder().build();
                    }
                    return null;
                }
        );
        s3StorageService.setS3Client(s3Proxy);

        Record recordProxy = (Record) Proxy.newProxyInstance(
                Record.class.getClassLoader(),
                new Class[]{Record.class},
                (proxy, method, args) -> Values.value("post-123")
        );

        TransactionContext txProxy = (TransactionContext) Proxy.newProxyInstance(
                TransactionContext.class.getClassLoader(),
                new Class[]{TransactionContext.class},
                (proxy, method, args) -> {
                    if ("run".equals(method.getName())) {
                        Value params = (Value) args[1];
                        String autor = params.get("autor").asString();
                        if ("error_db".equals(autor)) {
                            throw new RuntimeException("Error simulado en Neo4j");
                        }
                        boolean usuarioExiste = !"no_existe".equals(autor);
                        return (Result) Proxy.newProxyInstance(
                                Result.class.getClassLoader(),
                                new Class[]{Result.class},
                                (resProxy, resMethod, resArgs) -> {
                                    if ("hasNext".equals(resMethod.getName())) {
                                        return usuarioExiste;
                                    }
                                    if ("next".equals(resMethod.getName())) {
                                        return recordProxy;
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
                    if ("executeWrite".equals(method.getName())) {
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
    void testFlujoCompletoPublicaciones() throws Exception {
        File tempFile = Files.createTempFile("foto_prueba", ".jpg").toFile();
        Files.writeString(tempFile.toPath(), "contenido-imagen-prueba");
        tempFile.deleteOnExit();

        // 1. Crear una publicación de solo texto.
        given()
                .auth().oauth2(token("said"))
                .multiPart("texto", "Publicación sin archivo adjunto")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(201)
                .body("media_url", org.hamcrest.CoreMatchers.nullValue())
                .body(containsString("Publicación creada con éxito"));

        // 2. Crear publicación con imagen exitosamente y crear el bucket.
        given()
                .auth().oauth2(token("said"))
                .multiPart("texto", "Probando publicacion con MinIO y Neo4j")
                .multiPart("archivo", tempFile, "image/jpeg")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(201)
                .body(containsString("red-social-media"))
                .body(containsString("Publicación creada con éxito"));

        // 3. Crear publicación con video y reutilizar el bucket.
        given()
                .auth().oauth2(token("said"))
                .multiPart("texto", "Probando video en MinIO")
                .multiPart("archivo", tempFile, "video/mp4")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(201)
                .body(containsString("video"));

        // 4. Rechazar texto vacío o demasiado extenso.
        given()
                .auth().oauth2(token("said"))
                .multiPart("texto", "   ")
                .multiPart("archivo", tempFile, "image/jpeg")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(400);

        given()
                .auth().oauth2(token("said"))
                .multiPart("texto", "a".repeat(501))
        .when()
                .post("/api/posts")
        .then()
                .statusCode(400);

        // 5. Rechazar archivos que no sean imagen o video.
        given()
                .auth().oauth2(token("said"))
                .multiPart("texto", "Archivo no permitido")
                .multiPart("archivo", tempFile, "text/plain")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(400);

        // 6. Exigir una sesión autenticada.
        given()
                .multiPart("texto", "Post sin sesión")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(401);

        // 7. Informar cuando el usuario autenticado no existe.
        given()
                .auth().oauth2(token("no_existe"))
                .multiPart("texto", "Post de usuario fantasma")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(404);

        // 8. Informar cuando falla Neo4j.
        given()
                .auth().oauth2(token("error_db"))
                .multiPart("texto", "Post con fallo de base")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(500);

        // 9. Cubrir valores opcionales del servicio de almacenamiento.
        s3StorageService.subirArchivo("post-extra", null, "", tempFile.toPath());
    }
}
