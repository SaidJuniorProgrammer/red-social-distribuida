package com.redsocial.repository;

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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioRepositoryTest {

    @Test
    void buscaSoloCuentasRegistradasYExcluyeAlUsuarioActual() {
        AtomicReference<String> consultaEjecutada = new AtomicReference<>();
        AtomicReference<Value> parametros = new AtomicReference<>();

        Result result = (Result) Proxy.newProxyInstance(
                Result.class.getClassLoader(),
                new Class<?>[]{Result.class},
                (proxy, method, args) -> {
                    if ("list".equals(method.getName()) && args != null && args.length == 1) {
                        var apply = java.util.function.Function.class
                                .getMethod("apply", Object.class);
                        return List.of(
                                apply.invoke(args[0], registroDe("said")),
                                apply.invoke(args[0], registroDe("samantha"))
                        );
                    }
                    return null;
                }
        );

        TransactionContext transaction = (TransactionContext) Proxy.newProxyInstance(
                TransactionContext.class.getClassLoader(),
                new Class<?>[]{TransactionContext.class},
                (proxy, method, args) -> {
                    if ("run".equals(method.getName())) {
                        consultaEjecutada.set((String) args[0]);
                        parametros.set((Value) args[1]);
                        return result;
                    }
                    return null;
                }
        );

        Session session = (Session) Proxy.newProxyInstance(
                Session.class.getClassLoader(),
                new Class<?>[]{Session.class},
                (proxy, method, args) -> {
                    if ("executeRead".equals(method.getName())) {
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

        UsuarioRepository repository = new UsuarioRepository();
        repository.setDriver(driver);

        assertEquals(
                List.of("said", "samantha"),
                repository.buscarUsernames("sa", "oscar")
        );
        assertTrue(consultaEjecutada.get().contains("u.password_hash IS NOT NULL"));
        assertEquals("sa", parametros.get().get("busqueda").asString());
        assertEquals("oscar", parametros.get().get("usernameActual").asString());
    }

    private Record registroDe(String username) {
        return (Record) Proxy.newProxyInstance(
                Record.class.getClassLoader(),
                new Class<?>[]{Record.class},
                (proxy, method, args) -> "get".equals(method.getName())
                        ? Values.value(username)
                        : null
        );
    }
}
