package com.redsocial.service;

import com.redsocial.dto.PushNotificationPayload;
import com.redsocial.dto.PushSubscriptionRequest;

public interface WebPushGateway {

    void enviar(
            PushSubscriptionRequest subscription,
            PushNotificationPayload payload,
            String publicKey,
            String privateKey
    ) throws Exception;
}
