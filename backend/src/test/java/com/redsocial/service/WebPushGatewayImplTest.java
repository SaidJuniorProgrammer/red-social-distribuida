package com.redsocial.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redsocial.dto.PushSubscriptionRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPushGatewayImplTest {

    @Test
    void devuelveElEstadoRealDelServicioPush() throws Exception {
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(
                new ProtocolVersion("HTTP", 1, 1),
                201,
                "Created"
        ));
        WebPushGatewayImpl gateway = gateway(true, 1);

        assertEquals(201, gateway.esperarEntrega(CompletableFuture.completedFuture(response)));
    }

    @Test
    void cancelaUnEnvioQueSuperaElTiempoPermitido() {
        CompletableFuture<HttpResponse> pendingDelivery = new CompletableFuture<>();
        WebPushGatewayImpl gateway = gateway(true, 0);

        assertThrows(
                IOException.class,
                () -> gateway.esperarEntrega(pendingDelivery)
        );
        assertTrue(pendingDelivery.isCancelled());
    }

    @Test
    void vuelveAValidarElEndpointAntesDeEnviar() {
        WebPushGatewayImpl gateway = gateway(false, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> gateway.enviar(subscription(), null, "publica", "privada")
        );
    }

    private WebPushGatewayImpl gateway(boolean safeEndpoint, long timeout) {
        WebPushGatewayImpl gateway = new WebPushGatewayImpl();
        gateway.objectMapper = new ObjectMapper();
        gateway.pushEndpointValidator = new PushEndpointValidator() {
            @Override
            public boolean esSeguro(String endpoint) {
                return safeEndpoint;
            }
        };
        gateway.subject = "mailto:test@pachyweb.local";
        gateway.deliveryTimeoutSeconds = timeout;
        return gateway;
    }

    private PushSubscriptionRequest subscription() {
        return new PushSubscriptionRequest(
                "usuario",
                "https://fcm.googleapis.com/fcm/send/suscripcion",
                Map.of("p256dh", "no-se-utiliza", "auth", "no-se-utiliza")
        );
    }
}
