package com.redsocial.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redsocial.dto.PushNotificationPayload;
import com.redsocial.dto.PushSubscriptionRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.Security;

@ApplicationScoped
public class WebPushGatewayImpl implements WebPushGateway {

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "webpush.vapid.subject", defaultValue = "mailto:admin@pachyweb.local")
    String subject;

    @Override
    public void enviar(
            PushSubscriptionRequest subscription,
            PushNotificationPayload payload,
            String publicKey,
            String privateKey
    ) throws Exception {
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
        pushService.send(notification);
    }
}
