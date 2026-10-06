package com.redsocial.controller;

import com.redsocial.dto.PushNotificationPayload;
import com.redsocial.repository.GrafoSocialRepository;
import com.redsocial.service.WebPushService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
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
import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pruebas de integración para los endpoints de reacciones LIKE y notificaciones Web Push asociadas.
 */
@QuarkusTest
class ReaccionesIntegrationTest {

    @Inject
    GrafoSocialRepository grafoSocialRepository;

    @Inject
    WebPushService webPushService;

    @Inject
    PostController postController;

    /**
     * Configura el driver de Neo4j simulado en memoria antes de cada prueba.
     */
    @BeforeEach
    void configurarMocks() {
        TransactionContext txProxy = (TransactionContext) Proxy.newProxyInstance(
                TransactionContext.class.getClassLoader(),
                new Class[]{TransactionContext.class},
                (proxy, method, args) -> {
                    if ("run".equals(method.getName())) {
                        Value params = (Value) args[1];
                        String miId = params.get("miId").asString("");

                        if ("error_db".equals(miId)) {
                            throw new RuntimeException("Fallo simulado en Neo4j al reaccionar");
                        }

                        boolean tieneResultado = !"no_existe".equals(miId);
                        AtomicInteger contador = new AtomicInteger(tieneResultado ? 1 : 0);

                        Record likeRecord = (Record) Proxy.newProxyInstance(
                                Record.class.getClassLoader(),
                                new Class[]{Record.class},
                                (rProxy, rMethod, rArgs) -> {
                                    if ("get".equals(rMethod.getName())) {
                                        String campo = (String) rArgs[0];
                                        if ("autor".equals(campo)) {
                                            if ("sin_autor".equals(miId)) {
                                                return Values.NULL;
                                            }
                                            if ("autor_vacio".equals(miId)) {
                                                return Values.value("   ");
                                            }
                                            return Values.value("carlos");
                                        }
                                        if ("creado".equals(campo)) {
                                            return Values.value(!"like_repetido".equals(miId));
                                        }
                                        return Values.value("carlos");
                                    }
                                    return null;
                                }
                        );

                        return (Result) Proxy.newProxyInstance(
                                Result.class.getClassLoader(),
                                new Class[]{Result.class},
                                (resProxy, resMethod, resArgs) -> {
                                    if ("hasNext".equals(resMethod.getName())) {
                                        return contador.getAndDecrement() > 0;
                                    }
                                    if ("next".equals(resMethod.getName())) {
                                        return likeRecord;
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

        grafoSocialRepository.setDriver(driverProxy);
    }

    /**
     * Verifica el flujo completo de creación y eliminación de Likes, validaciones de seguridad y Web Push.
     */
    @Test
    void testDarYQuitarLikeConNotificacionPush() {
        // 1. Dar Like de otro usuario ('said' -> post de 'carlos'): registra LIKE y emite evento Push
        given()
        .when()
                .post("/api/posts/p1/like/said")
        .then()
                .statusCode(200)
                .body(containsString("Reacción LIKE registrada exitosamente"));

        List<PushNotificationPayload> notificacionesCarlos = webPushService.obtenerNotificacionesDeUsuario("carlos");
        assertFalse(notificacionesCarlos.isEmpty());

        // 2. Casos de idempotencia: auto-like, like repetido y post sin autor (200 sin duplicar Push)
        given().when().post("/api/posts/p1/like/carlos").then().statusCode(200);
        given().when().post("/api/posts/p1/like/like_repetido").then().statusCode(200);
        given().when().post("/api/posts/p1/like/sin_autor").then().statusCode(200);
        given().when().post("/api/posts/p1/like/autor_vacio").then().statusCode(200);

        // 3. Validación de seguridad: usuario autenticado intentando reaccionar por otro (403 Forbidden)
        SecurityContext ctxSaid = crearSecurityContext("said");
        try (Response forbiddenLike = postController.darLikePost("p1", "otro_usuario", ctxSaid)) {
            assertEquals(403, forbiddenLike.getStatus());
        }
        try (Response allowedLike = postController.darLikePost("p1", "said", ctxSaid)) {
            assertEquals(200, allowedLike.getStatus());
        }
        try (Response forbiddenUnlike = postController.quitarLikePost("p1", "otro_usuario", ctxSaid)) {
            assertEquals(403, forbiddenUnlike.getStatus());
        }

        // 4. Dar Like con usuario/post inexistente (404) y error de base de datos (500)
        given().when().post("/api/posts/p1/like/no_existe").then().statusCode(404);
        given().when().post("/api/posts/p1/like/error_db").then().statusCode(500);

        // 5. Quitar Like exitoso (200), inexistente (404) y error de base de datos (500)
        given()
        .when()
                .delete("/api/posts/p1/like/said")
        .then()
                .statusCode(200)
                .body(containsString("Reacción LIKE eliminada exitosamente"));

        given().when().delete("/api/posts/p1/like/no_existe").then().statusCode(404);
        given().when().delete("/api/posts/p1/like/error_db").then().statusCode(500);
    }

    /**
     * Crea un SecurityContext simulado con el nombre de usuario indicado.
     *
     * @param username nombre del principal autenticado
     * @return instancia de SecurityContext
     */
    private SecurityContext crearSecurityContext(String username) {
        Principal principal = () -> username;
        return (SecurityContext) Proxy.newProxyInstance(
                SecurityContext.class.getClassLoader(),
                new Class[]{SecurityContext.class},
                (proxy, method, args) -> "getUserPrincipal".equals(method.getName()) ? principal : null
        );
    }
}