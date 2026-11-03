package com.challenge.order_processing_api.messaging;

import com.challenge.order_processing_api.config.OrdersProperties;
import com.challenge.order_processing_api.dto.response.OrderMessage;
import com.challenge.order_processing_api.service.ClaimedOutboxEvent;
import com.challenge.order_processing_api.service.OutboxClaimService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxClaimService outboxClaimService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final String routingKey;

    public OutboxPublisher(
            OutboxClaimService outboxClaimService,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            OrdersProperties ordersProperties) {
        this.outboxClaimService = outboxClaimService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = ordersProperties.rabbitmq().exchange();
        this.routingKey = ordersProperties.rabbitmq().routingKey();
    }

    public void publishPendingEvents() {
        Optional<ClaimedOutboxEvent> nextEvent = outboxClaimService.claimNext();
        while (nextEvent.isPresent()) {
            ClaimedOutboxEvent event = nextEvent.get();
            try {
                OrderMessage message = objectMapper.readValue(event.payload(), OrderMessage.class);
                publish(event, message);
                if (!outboxClaimService.markPublished(event)) {
                    logger.warn("Evento Outbox {} foi publicado, mas sua reserva expirou antes da confirmação local.",
                            event.id());
                }
            } catch (JsonProcessingException exception) {
                logFailure(event, "possui payload inválido; será retomado após expirar a reserva", exception);
                return;
            } catch (InterruptedException exception) {
                outboxClaimService.releaseClaim(event);
                Thread.currentThread().interrupt();
                logger.error("Publicação do evento Outbox {} foi interrompida.", event.id(), exception);
                return;
            } catch (Exception exception) {
                logFailure(event, "falhou ao publicar; será retomado após expirar a reserva", exception);
                return;
            }
            nextEvent = outboxClaimService.claimNext();
        }
    }

    private void logFailure(ClaimedOutboxEvent event, String message, Exception exception) {
        logger.error("Evento Outbox {} {}.", event.id(), message, exception);
    }

    private void publish(ClaimedOutboxEvent event, OrderMessage message) throws Exception {
        CorrelationData correlationData = new CorrelationData(event.id().toString());
        rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);
        CorrelationData.Confirm confirmation = correlationData.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirmation.isAck()) {
            throw new AmqpException("RabbitMQ rejeitou a publicação do evento " + event.id()
                    + ": " + confirmation.getReason());
        }
        if (correlationData.getReturned() != null) {
            throw new AmqpException("RabbitMQ não roteou o evento " + event.id());
        }
    }
}
