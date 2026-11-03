package com.challenge.order_processing_api.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.challenge.order_processing_api.model.OutboxEvent;
import com.challenge.order_processing_api.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class OutboxPublisherTest {

    @Test
    void retriesAnOutboxEventAfterBrokerFailure() {
        OutboxEventRepository repository = org.mockito.Mockito.mock(OutboxEventRepository.class);
        RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "ORDER_CREATED",
                "{\"orderId\":\"" + UUID.randomUUID() + "\"}");
        when(repository.findPendingForUpdate()).thenReturn(List.of(event));

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
                repository, rabbitTemplate, new ObjectMapper(), "orders.exchange", "orders.created");

        publisher.publishPendingEvents();
        assertThat(event.getPublishedAt()).isNull();

        publisher.publishPendingEvents();
        assertThat(event.getPublishedAt()).isNotNull();
        verify(repository, org.mockito.Mockito.times(2)).findPendingForUpdate();
    }
}
