package com.redsocial.dto;

import java.util.List;

public record ChatHistory(
        String type,
        List<ChatMessage> messages
) {
}
