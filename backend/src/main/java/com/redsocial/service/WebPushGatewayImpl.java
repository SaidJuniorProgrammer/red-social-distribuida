package com.redsocial.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redsocial.dto.PushNotificationPayload;
import com.redsocial.dto.PushSubscriptionRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.security.Security;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class WebPushGatewayImpl implements WebPushGateway {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    PushEndpointValidator pushEndpointValidator;

    @ConfigProperty(name = "webpush.vapid.subject", defaultValue = "mailto:admin@pachyweb.local")
    String subject;

    @ConfigProperty(name = "webpush.delivery-timeout-seconds", defaultValue = "10")
    long deliveryTimeoutSeconds;

    @Override
    public int enviar(
            PushSubscriptionRequest subscription,
            PushNotificationPayload payload,
            String publicKey,
            String privateKey
    ) throws Exception {
        if (!pushEndpointValidator.esSeguro(subscription.endpoint())) {
            throw new IllegalArgumentException("El endpoint Web Push no es seguro.");
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        PushService pushService = new PushService(publicKey, privateKey, subject);
        Notification notification = new Notification(
                subscription.endpoint(),
                subscription.keys().get("p256dh"),
                subscription.keys().get("auth"),
                objectMapper.writeValueAsString(payload)
        );
        return esperarEntrega(pushService.sendAsync(notification));
    }

    int esperarEntrega(Future<HttpResponse> delivery) throws Exception {
        try {
            HttpResponse response = delivery.get(deliveryTimeoutSeconds, TimeUnit.SECONDS);
            return response.getStatusLine().getStatusCode();
        } catch (TimeoutException exception) {
            delivery.cancel(true);
            throw new IOException("El servicio Push agotó el tiempo de espera.", exception);
        } catch (InterruptedException exception) {
            delivery.cancel(true);
            Thread.currentThread().interrupt();
            throw exception;
        }
    }
}
