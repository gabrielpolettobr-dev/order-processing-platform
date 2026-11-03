package com.challenge.order_processing_api.messaging.consumer;

import com.challenge.order_processing_api.messaging.dto.OrderMessage;
import com.challenge.order_processing_api.service.OrderProcessingStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;

@Component
public class OrderMessageRecoverer implements MessageRecoverer {

    private static final Logger logger = LoggerFactory.getLogger(OrderMessageRecoverer.class);

    private final ObjectMapper objectMapper;
    private final OrderProcessingStateService processingStateService;

    public OrderMessageRecoverer(
            ObjectMapper objectMapper,
            OrderProcessingStateService processingStateService) {
        this.objectMapper = objectMapper;
        this.processingStateService = processingStateService;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        try {
            OrderMessage orderMessage = objectMapper.readValue(message.getBody(), OrderMessage.class);
            processingStateService.markFailed(orderMessage.orderId());
            logger.error("Mensagem do pedido {} esgotou as tentativas e será encaminhada à DLQ.",
                    orderMessage.orderId(), cause);
        } catch (IOException exception) {
            logger.error("Mensagem inválida esgotou as tentativas e será encaminhada à DLQ.", cause);
        }
        throw new AmqpRejectAndDontRequeueException("Tentativas de processamento esgotadas.", cause);
    }
}
