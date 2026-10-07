package com.redsocial.repository;

import com.redsocial.dto.NotificacionResponse;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulador global del repositorio con prioridad alta (@Alternative).
 * Garantiza que los tests en GitHub Actions no intenten conectar a un Neo4j inexistente.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class MockNotificacionRepository extends NotificacionRepository {

    @Override
    public void guardarNotificacion(String idNotificacion, String tipo, String actor,
                                    String destinatario, String mensaje, String referencia, String fecha) {
        // Interceptamos la llamada y no hacemos nada para evitar el SocketTimeout
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