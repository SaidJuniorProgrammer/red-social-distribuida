package com.redsocial.controller;

import com.redsocial.dto.ApiErrorResponse;
import com.redsocial.dto.UsuarioResumenResponse;
import com.redsocial.repository.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsuarioControllerTest {

    @Test
    void devuelveSoloLosUsuariosRegistradosQueEntregaElRepositorio() {
        GrafoSocialController controller = crearController(new UsuarioRepository() {
            @Override
            public List<String> buscarUsernames(String busqueda, String usernameActual) {
                assertEquals("sa", busqueda);
                assertEquals("oscar", usernameActual);
                return List.of("said", "samantha");
            }
        });

        try (Response response = controller.buscarUsuarios("  sa  ")) {
            assertEquals(200, response.getStatus());

            Map<?, ?> entity = (Map<?, ?>) response.getEntity();
            List<?> usuarios = (List<?>) entity.get("usuarios");
            assertEquals(
                    List.of(new UsuarioResumenResponse("said"), new UsuarioResumenResponse("samantha")),
                    usuarios
            );
        }
    }

    @Test
    void noConsultaLaBaseCuandoLaBusquedaEstaVacia() {
        GrafoSocialController controller = crearController(new UsuarioRepository() {
            @Override
            public List<String> buscarUsernames(String busqueda, String usernameActual) {
                throw new AssertionError("No debe consultar el repositorio");
            }
        });

        try (Response response = controller.buscarUsuarios("   ")) {
            assertEquals(200, response.getStatus());
            assertEquals(List.of(), ((Map<?, ?>) response.getEntity()).get("usuarios"));
        }
    }

    @Test
    void respondeConUnMensajeClaroCuandoLaBusquedaFalla() {
        GrafoSocialController controller = crearController(new UsuarioRepository() {
            @Override
            public List<String> buscarUsernames(String busqueda, String usernameActual) {
                throw new IllegalStateException("Neo4j no disponible");
            }
        });

        try (Response response = controller.buscarUsuarios("said")) {
            assertEquals(500, response.getStatus());
            assertEquals(
                    "No pudimos buscar usuarios en este momento.",
                    ((ApiErrorResponse) response.getEntity()).message()
            );
        }
    }

    private GrafoSocialController crearController(UsuarioRepository usuarioRepository) {
        GrafoSocialController controller = new GrafoSocialController();
        controller.usuarioRepository = usuarioRepository;
        controller.securityIdentity = identidadDe("oscar");
        return controller;
    }

    private SecurityIdentity identidadDe(String username) {
        Principal principal = () -> username;
        return (SecurityIdentity) Proxy.newProxyInstance(
                SecurityIdentity.class.getClassLoader(),
                new Class<?>[]{SecurityIdentity.class},
                (proxy, method, args) -> "getPrincipal".equals(method.getName()) ? principal : null
        );
    }
}
