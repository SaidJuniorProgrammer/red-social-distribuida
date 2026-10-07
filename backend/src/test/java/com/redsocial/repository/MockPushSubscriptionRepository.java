package com.redsocial.repository;

import com.redsocial.dto.PushSubscriptionRequest;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulador global para evitar que el WebPushService intente conectarse a Neo4j 
 * durante su inicialización (@PostConstruct) en el entorno de GitHub Actions.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class MockPushSubscriptionRepository extends PushSubscriptionRepository {

    @Override
    public void guardar(PushSubscriptionRequest subscription) {
        // No hacemos nada en pruebas
    }

    @Override
    public boolean eliminar(String usuario, String endpoint) {
        return false;
    }

    @Override
    public List<PushSubscriptionRequest> obtenerTodas() {
        // Devolvemos una lista vacía para que el servicio inicie al instante sin buscar en Neo4j
        return new ArrayList<>();
    }
}