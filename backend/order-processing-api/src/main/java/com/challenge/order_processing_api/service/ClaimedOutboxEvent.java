package com.challenge.order_processing_api.service;

import java.util.UUID;

public record ClaimedOutboxEvent(UUID id, String payload, UUID claimToken) {
}
