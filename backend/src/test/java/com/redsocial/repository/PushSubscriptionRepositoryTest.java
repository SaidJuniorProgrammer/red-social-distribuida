package com.redsocial.repository;

import com.redsocial.dto.PushSubscriptionRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushSubscriptionRepositoryTest {

    @Test
    void guardaEliminaYRestauraLasSuscripciones() {
        PushSubscriptionRequest subscription = new PushSubscriptionRequest(
                "gualter",
                "https://fcm.googleapis.com/fcm/send/navegador",
                Map.of("p256dh", "clave-publica", "auth", "secreto")
        );
        List<String> consultas = new ArrayList<>();
        List<Value> parametros = new ArrayList<>();
        AtomicBoolean suscripcionExistente = new AtomicBoolean(true);

        Result result = resultadoDe(subscription, suscripcionExistente);
        TransactionContext transaction = (TransactionContext) Proxy.newProxyInstance(
                TransactionContext.class.getClassLoader(),
                new Class<?>[]{TransactionContext.class},
                (proxy, method, args) -> {
                    if ("run".equals(method.getName())) {
                        consultas.add((String) args[0]);
                        if (args.length > 1) {
                            parametros.add((Value) args[1]);
                        }
                        return result;
                    }
                    return null;
                }
        );
        Session session = (Session) Proxy.newProxyInstance(
                Session.class.getClassLoader(),
                new Class<?>[]{Session.class},
                (proxy, method, args) -> {
                    if ("executeWrite".equals(method.getName())
                            || "executeRead".equals(method.getName())) {
                        TransactionCallback<?> callback = (TransactionCallback<?>) args[0];
                        return callback.execute(transaction);
                    }
                    return null;
                }
        );
        Driver driver = (Driver) Proxy.newProxyInstance(
                Driver.class.getClassLoader(),
                new Class<?>[]{Driver.class},
                (proxy, method, args) -> "session".equals(method.getName()) ? session : null
        );

        PushSubscriptionRepository repository = new PushSubscriptionRepository();
        repository.driver = driver;

        repository.guardar(subscription);
        assertTrue(repository.eliminar(subscription.usuario(), subscription.endpoint()));
        suscripcionExistente.set(false);
        assertFalse(repository.eliminar(subscription.usuario(), subscription.endpoint()));
        assertEquals(List.of(subscription), repository.obtenerTodas());

        assertTrue(consultas.get(0).contains("usuario.password_hash IS NOT NULL"));
        assertTrue(consultas.get(3).contains("usuario.password_hash IS NOT NULL"));
        assertEquals("gualter", parametros.get(0).get("usuario").asString());
        assertEquals(subscription.endpoint(), parametros.get(0).get("endpoint").asString());
    }

    private Result resultadoDe(
            PushSubscriptionRequest subscription,
            AtomicBoolean suscripcionExistente
    ) {
        Record record = (Record) Proxy.newProxyInstance(
                Record.class.getClassLoader(),
                new Class<?>[]{Record.class},
                (proxy, method, args) -> {
                    if (!"get".equals(method.getName())) {
                        return null;
                    }
                    return switch ((String) args[0]) {
                        case "usuario" -> Values.value(subscription.usuario());
                        case "endpoint" -> Values.value(subscription.endpoint());
                        case "p256dh" -> Values.value(subscription.keys().get("p256dh"));
                        case "auth" -> Values.value(subscription.keys().get("auth"));
                        case "eliminadas" -> Values.value(1L);
                        default -> Values.NULL;
                    };
                }
        );

        return (Result) Proxy.newProxyInstance(
                Result.class.getClassLoader(),
                new Class<?>[]{Result.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasNext" -> suscripcionExistente.get();
                    case "next" -> record;
                    case "list" -> aplicarMapeo(args[0], record);
                    default -> null;
                }
        );
    }

    private List<?> aplicarMapeo(Object mapper, Record record) throws ReflectiveOperationException {
        var apply = java.util.function.Function.class.getMethod("apply", Object.class);
        return List.of(apply.invoke(mapper, record));
    }
}
