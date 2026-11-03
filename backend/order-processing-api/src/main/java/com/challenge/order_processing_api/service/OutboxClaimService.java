package com.challenge.order_processing_api.service;

import com.challenge.order_processing_api.config.OrdersProperties;
import com.challenge.order_processing_api.repository.OutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxClaimService {

    private final OutboxEventRepository outboxEventRepository;
    private final Duration claimTimeout;

    public OutboxClaimService(
            OutboxEventRepository outboxEventRepository,
            OrdersProperties ordersProperties) {
        Duration claimTimeout = ordersProperties.outbox().claimTimeout();
        if (claimTimeout.isNegative() || claimTimeout.isZero()) {
            throw new IllegalArgumentException("O tempo de reserva da Outbox deve ser positivo.");
        }
        this.outboxEventRepository = outboxEventRepository;
        this.claimTimeout = claimTimeout;
    }

    @Transactional
    public Optional<ClaimedOutboxEvent> claimNext() {
        Instant now = Instant.now();
        return outboxEventRepository.findNextPendingForUpdate(now)
                .map(event -> {
                    UUID claimToken = UUID.randomUUID();
                    event.claim(claimToken, now.plus(claimTimeout));
                    return new ClaimedOutboxEvent(event.getId(), event.getPayload(), claimToken);
                });
    }

    @Transactional
    public boolean markPublished(ClaimedOutboxEvent event) {
        return outboxEventRepository.markPublished(event.id(), event.claimToken(), Instant.now()) == 1;
    }

    @Transactional
    public boolean releaseClaim(ClaimedOutboxEvent event) {
        return outboxEventRepository.releaseClaim(event.id(), event.claimToken()) == 1;
    }
}
