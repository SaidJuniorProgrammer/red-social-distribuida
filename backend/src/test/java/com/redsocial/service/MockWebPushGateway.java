package com.redsocial.service;

import com.redsocial.dto.PushNotificationPayload;
import com.redsocial.dto.PushSubscriptionRequest;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Simulador global del WebPushGateway para pruebas.
 * Evita que los tests intenten hacer peticiones HTTP reales a Google FCM o servidores Push,
 * previniendo errores de SocketTimeout en el entorno cerrado de GitHub Actions.
 */
@Mock
@ApplicationScoped
public class MockWebPushGateway implements WebPushGateway {

    @Override
    public int enviar(PushSubscriptionRequest subscription, PushNotificationPayload payload, String publicKey, String privateKey) {
        // Simulamos que la petición HTTP al servidor de notificaciones fue exitosa (201 Created)
        // Esto permite que el flujo de Java termine en 1 milisegundo sin hacer tráfico de red.
        return 201;
    }
}