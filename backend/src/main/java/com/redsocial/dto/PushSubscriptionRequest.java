package com.redsocial.dto;

import java.util.Map;

public record PushSubscriptionRequest(
        String usuario,
        String endpoint,
        Map<String, String> keys
) {
}