package com.redsocial.controller;

import com.redsocial.dto.PushSubscriptionRequest;
import com.redsocial.repository.PostRepository;
import com.redsocial.repository.PushSubscriptionRepository;
import com.redsocial.service.S3StorageService;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.jboss.resteasy.reactive.multipart.FileUpload;
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
import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class PostIntegrationTest {

    @Inject
    PostRepository postRepository;

    @Inject
    S3StorageService s3StorageService;

    @Inject
    PostController postController;

    private final AtomicBoolean bucketExiste = new AtomicBoolean(false);

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

        // 1. Crear post con imagen exitosamente (201 - crea bucket por primera vez)
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

        // 2. Crear post con video (201 - usa bucket ya existente y cubre rama 'video')
        given()
                .multiPart("texto", "Probando video en MinIO")
                .multiPart("autor", "said")
                .multiPart("archivo", tempFile, "video/mp4")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(201)
                .body(containsString("video"));

        // 3. Error 400 cuando falta el texto o está en blanco
        given()
                .multiPart("texto", "   ")
                .multiPart("autor", "said")
                .multiPart("archivo", tempFile, "image/jpeg")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(400);

        // 4. Error 400 cuando falta el archivo multimedia
        given()
                .multiPart("texto", "Post sin archivo adjunto")
                .multiPart("autor", "said")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(400);

        // 5. Error 401 cuando falta el autor o está en blanco
        given()
                .multiPart("texto", "Post sin autor")
                .multiPart("autor", "   ")
                .multiPart("archivo", tempFile, "image/jpeg")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(401);

        // 6. Error 404 cuando el usuario autor no existe en Neo4j
        given()
                .multiPart("texto", "Post de usuario fantasma")
                .multiPart("autor", "no_existe")
                .multiPart("archivo", tempFile, "image/jpeg")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(404);

        // 7. Error 500 cuando falla Neo4j
        given()
                .multiPart("texto", "Post con fallo de base")
                .multiPart("autor", "error_db")
                .multiPart("archivo", tempFile, "image/jpeg")
        .when()
                .post("/api/posts")
        .then()
                .statusCode(500);

        // 8. Cubrir extracción de autor desde SecurityContext (JWT Principal) y valores nulos en S3
        s3StorageService.subirArchivo("post-extra", null, "", tempFile.toPath());

        FileUpload fileUploadMock = (FileUpload) Proxy.newProxyInstance(
                FileUpload.class.getClassLoader(),
                new Class[]{FileUpload.class},
                (proxy, method, args) -> {
                    if ("uploadedFile".equals(method.getName())) return tempFile.toPath();
                    if ("fileName".equals(method.getName())) return "foto.png";
                    if ("contentType".equals(method.getName())) return null;
                    return null;
                }
        );

        SecurityContext securityContextConUsuario = (SecurityContext) Proxy.newProxyInstance(
                SecurityContext.class.getClassLoader(),
                new Class[]{SecurityContext.class},
                (proxy, method, args) -> "getUserPrincipal".equals(method.getName())
                        ? (Principal) () -> "said_jwt"
                        : null
        );

        try (Response resp = postController.crearPost("Post autenticado por JWT", null, fileUploadMock, securityContextConUsuario)) {
            assertEquals(201, resp.getStatus());
        }
    }
}
