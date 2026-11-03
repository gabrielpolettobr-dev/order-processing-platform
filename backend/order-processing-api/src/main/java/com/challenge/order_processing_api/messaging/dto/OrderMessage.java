package com.challenge.order_processing_api.messaging.dto;

import java.util.UUID;

public record OrderMessage(UUID orderId) {
}
