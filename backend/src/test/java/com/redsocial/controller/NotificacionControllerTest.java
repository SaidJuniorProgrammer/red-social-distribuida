package com.redsocial.controller;

import com.redsocial.repository.NotificacionRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class NotificacionControllerTest {

    @InjectMock
    NotificacionRepository notificacionRepository;

    // Generamos un token válido para pasar la seguridad sin usar librerías extra
    private String token() {
        return Jwt.issuer("https://redsocial.com/issuer")
                .upn("testuser")
                .groups("Usuario")
                .sign();
    }

    @Test
    void testListarNotificaciones() {
        Mockito.when(notificacionRepository.listarNotificaciones("testuser")).thenReturn(List.of());
        given().auth().oauth2(token())
               .when().get("/api/notificaciones")
               .then().statusCode(200);
    }

    @Test
    void testContarNoLeidas() {
        Mockito.when(notificacionRepository.contarNoLeidas("testuser")).thenReturn(5L);
        given().auth().oauth2(token())
               .when().get("/api/notificaciones/no-leidas")
               .then().statusCode(200).body("no_leidas", is(5));
    }

    @Test
    void testMarcarComoLeida() {
        Mockito.when(notificacionRepository.marcarComoLeida("123", "testuser")).thenReturn(true);
        given().auth().oauth2(token())
               .when().put("/api/notificaciones/123/leer")
               .then().statusCode(200);

        Mockito.when(notificacionRepository.marcarComoLeida("999", "testuser")).thenReturn(false);
        given().auth().oauth2(token())
               .when().put("/api/notificaciones/999/leer")
               .then().statusCode(404);
    }

    @Test
    void testMarcarTodasComoLeidas() {
        Mockito.when(notificacionRepository.marcarTodasComoLeidas("testuser")).thenReturn(3L);
        given().auth().oauth2(token())
               .when().put("/api/notificaciones/leer-todas")
               .then().statusCode(200).body("actualizadas", is(3));
    }
}