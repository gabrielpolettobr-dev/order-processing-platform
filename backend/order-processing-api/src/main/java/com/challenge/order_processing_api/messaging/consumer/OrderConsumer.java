package com.challenge.order_processing_api.messaging.consumer;

import com.challenge.order_processing_api.messaging.dto.OrderMessage;
import com.challenge.order_processing_api.service.OrderProcessingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumer {

    private final OrderProcessingService orderProcessingService;

    public OrderConsumer(OrderProcessingService orderProcessingService) {
        this.orderProcessingService = orderProcessingService;
    }

    @RabbitListener(queues = "#{@ordersQueue.name}")
    public void consume(OrderMessage message) {
        orderProcessingService.process(message.orderId());
    }
}
