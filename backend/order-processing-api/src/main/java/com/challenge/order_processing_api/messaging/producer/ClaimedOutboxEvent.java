package com.challenge.order_processing_api.messaging.producer;

import java.util.UUID;

public record ClaimedOutboxEvent(UUID id, String payload, UUID claimToken) {
}
