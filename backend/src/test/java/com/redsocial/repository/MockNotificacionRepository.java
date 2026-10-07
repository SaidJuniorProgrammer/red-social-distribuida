package com.redsocial.repository;

import com.redsocial.dto.NotificacionResponse;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulador global del repositorio para evitar que los tests de integración 
 * se queden colgados intentando conectar a un Neo4j inexistente en GitHub Actions.
 */
@Mock
@ApplicationScoped
public class MockNotificacionRepository extends NotificacionRepository {

    @Override
    public void guardarNotificacion(String idNotificacion, String tipo, String actor,
                                    String destinatario, String mensaje, String referencia, String fecha) {
        // No hacemos nada para no bloquear los tests en el servidor de CI
    }

    @Override
    public List<NotificacionResponse> listarNotificaciones(String destinatario) {
        return new ArrayList<>();
    }

    @Override
    public boolean marcarComoLeida(String idNotificacion, String destinatario) {
        return true;
    }

    @Override
    public long marcarTodasComoLeidas(String destinatario) {
        return 0L;
    }

    @Override
    public long contarNoLeidas(String destinatario) {
        return 0L;
    }
}