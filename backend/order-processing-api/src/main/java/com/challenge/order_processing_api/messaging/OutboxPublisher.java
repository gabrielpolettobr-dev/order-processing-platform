package com.challenge.order_processing_api.messaging;

import com.challenge.order_processing_api.dto.response.OrderMessage;
import com.challenge.order_processing_api.model.OutboxEvent;
import com.challenge.order_processing_api.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final String routingKey;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${orders.rabbitmq.exchange}") String exchange,
            @Value("${orders.rabbitmq.routing-key}") String routingKey) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Scheduled(fixedDelayString = "${orders.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findPendingForUpdate();
        for (OutboxEvent event : events) {
            try {
                OrderMessage message = objectMapper.readValue(event.getPayload(), OrderMessage.class);
                publish(event, message);
                event.markPublished();
            } catch (JsonProcessingException exception) {
                logger.error("Evento Outbox {} possui payload inválido e não foi publicado.", event.getId(), exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                logger.error("Publicação do evento Outbox {} foi interrompida.", event.getId(), exception);
                return;
            } catch (Exception exception) {
                logger.error("Falha ao publicar evento Outbox {}. Ele será tentado novamente.", event.getId(), exception);
            }
        }
    }

    private void publish(OutboxEvent event, OrderMessage message) throws Exception {
        CorrelationData correlationData = new CorrelationData(event.getId().toString());
        rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);
        CorrelationData.Confirm confirmation = correlationData.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirmation.isAck()) {
            throw new AmqpException("RabbitMQ rejeitou a publicação do evento " + event.getId()
                    + ": " + confirmation.getReason());
        }
        if (correlationData.getReturned() != null) {
            throw new AmqpException("RabbitMQ não roteou o evento " + event.getId());
        }
    }
}
