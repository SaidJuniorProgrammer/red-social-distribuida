package com.redsocial.controller;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
public class AuthIntegrationTest {

    @Test
    public void testFlujoCompletoAutenticacion() {
        // Generamos un usuario aleatorio para no tener conflictos en Neo4j en cada prueba
        String testUser = "user_" + UUID.randomUUID().toString().substring(0, 8);
        String testPassword = "password123";

        // 1. Probar el Registro (Simula tu POST de registro)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + testUser + "\",\"email\":\"" + testUser + "@test.com\",\"password\":\"" + testPassword + "\"}")
        .when()
            .post("/api/auth/register")
        .then()
            .statusCode(201)
            .body(containsString("Usuario registrado con éxito"));

        // 2. Probar el Login Exitoso (Simula tu POST de login)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + testUser + "\",\"password\":\"" + testPassword + "\"}")
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body(containsString("token"));

        // 3. Probar Login Fallido (Contraseña incorrecta)
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + testUser + "\",\"password\":\"mala_clave\"}")
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401);
    }
}