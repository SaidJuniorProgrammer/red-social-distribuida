package com.redsocial.controller;

import com.redsocial.repository.PostRepository;
import com.redsocial.service.S3StorageService;
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
import java.nio.file.Files;
import java.lang.reflect.Proxy;
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

    @BeforeEach
    void configurarMocksEnMemoria() {
        bucketExiste.set(false);

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

        // 1. Crear post con imagen exitosamente (201)
        given()
                .multiPart("texto", "Probando publicacion con MinIO y Neo4j")
                .multiPart("autor", "said")
                .multiPart("archivo", tempFile, "image/jpeg")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(201)
                .body(containsString("red-social-media"))
                .body(containsString("Publicación creada con éxito"));

        // 2. Error 400 cuando falta el texto
        given()
                .multiPart("autor", "said")
                .multiPart("archivo", tempFile, "image/jpeg")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(400);

        // 3. Error 401 cuando falta el autor
        given()
                .multiPart("texto", "Post sin autor")
                .multiPart("archivo", tempFile, "image/jpeg")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(401);

        // 4. Error 404 cuando el usuario autor no existe en Neo4j
        given()
                .multiPart("texto", "Post de usuario fantasma")
                .multiPart("autor", "no_existe")
                .multiPart("archivo", tempFile, "image/jpeg")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(404);

        // 5. Error 500 cuando falla Neo4j
        given()
                .multiPart("texto", "Post con fallo de base")
                .multiPart("autor", "error_db")
                .multiPart("archivo", tempFile, "image/jpeg")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(500);
    }
}