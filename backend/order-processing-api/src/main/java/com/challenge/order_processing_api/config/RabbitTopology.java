package com.challenge.order_processing_api.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopology {

    @Bean
    DirectExchange ordersExchange(@Value("${orders.rabbitmq.exchange}") String exchange) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    DirectExchange ordersDeadLetterExchange(@Value("${orders.rabbitmq.dead-letter-exchange}") String exchange) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    Queue ordersQueue(
            @Value("${orders.rabbitmq.queue}") String queue,
            @Value("${orders.rabbitmq.dead-letter-exchange}") String deadLetterExchange,
            @Value("${orders.rabbitmq.dead-letter-routing-key}") String deadLetterRoutingKey) {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }

    @Bean
    Queue ordersDeadLetterQueue(@Value("${orders.rabbitmq.dead-letter-queue}") String queue) {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    Binding ordersBinding(
            @Qualifier("ordersQueue") Queue ordersQueue,
            @Qualifier("ordersExchange") DirectExchange ordersExchange,
            @Value("${orders.rabbitmq.routing-key}") String routingKey) {
        return BindingBuilder.bind(ordersQueue).to(ordersExchange).with(routingKey);
    }

    @Bean
    Binding ordersDeadLetterBinding(
            @Qualifier("ordersDeadLetterQueue") Queue ordersDeadLetterQueue,
            @Qualifier("ordersDeadLetterExchange") DirectExchange ordersDeadLetterExchange,
            @Value("${orders.rabbitmq.dead-letter-routing-key}") String routingKey) {
        return BindingBuilder.bind(ordersDeadLetterQueue).to(ordersDeadLetterExchange).with(routingKey);
    }
}
