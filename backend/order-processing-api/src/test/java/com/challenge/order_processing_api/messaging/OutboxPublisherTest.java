package com.challenge.order_processing_api.messaging;

import static com.challenge.order_processing_api.TestOrdersProperties.defaults;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.challenge.order_processing_api.dto.response.OrderMessage;
import com.challenge.order_processing_api.service.ClaimedOutboxEvent;
import com.challenge.order_processing_api.service.OutboxClaimService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class OutboxPublisherTest {

    @Test
    void retriesAnOutboxEventAfterBrokerFailure() {
        OutboxClaimService claimService = org.mockito.Mockito.mock(OutboxClaimService.class);
        RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ClaimedOutboxEvent firstClaim = new ClaimedOutboxEvent(
                eventId, "{\"orderId\":\"" + orderId + "\"}", UUID.randomUUID());
        ClaimedOutboxEvent retryClaim = new ClaimedOutboxEvent(
                eventId, "{\"orderId\":\"" + orderId + "\"}", UUID.randomUUID());
        when(claimService.claimNext())
                .thenReturn(Optional.of(firstClaim), Optional.of(retryClaim), Optional.empty());

        AtomicInteger publishAttempts = new AtomicInteger();
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            if (publishAttempts.incrementAndGet() == 1) {
                throw new AmqpException("simulated broker outage");
            }
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(com.challenge.order_processing_api.dto.response.OrderMessage.class),
                any(CorrelationData.class));

        OutboxPublisher publisher = new OutboxPublisher(
                claimService, rabbitTemplate, new ObjectMapper(), defaults());

        publisher.publishPendingEvents();
        verify(claimService, org.mockito.Mockito.never()).markPublished(firstClaim);
        verify(claimService, org.mockito.Mockito.never()).releaseClaim(firstClaim);

        publisher.publishPendingEvents();
        verify(claimService).markPublished(retryClaim);
        verify(claimService, org.mockito.Mockito.times(3)).claimNext();
    }
}
