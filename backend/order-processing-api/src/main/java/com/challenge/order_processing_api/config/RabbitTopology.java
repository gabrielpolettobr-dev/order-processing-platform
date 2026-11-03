package com.challenge.order_processing_api.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopology {

    @Bean
    DirectExchange ordersExchange(OrdersProperties properties) {
        return new DirectExchange(properties.rabbitmq().exchange(), true, false);
    }

    @Bean
    DirectExchange ordersDeadLetterExchange(OrdersProperties properties) {
        return new DirectExchange(properties.rabbitmq().deadLetterExchange(), true, false);
    }

    @Bean
    Queue ordersQueue(
            OrdersProperties properties) {
        return QueueBuilder.durable(properties.rabbitmq().queue())
                .deadLetterExchange(properties.rabbitmq().deadLetterExchange())
                .deadLetterRoutingKey(properties.rabbitmq().deadLetterRoutingKey())
                .build();
    }

    @Bean
    Queue ordersDeadLetterQueue(OrdersProperties properties) {
        return QueueBuilder.durable(properties.rabbitmq().deadLetterQueue()).build();
    }

    @Bean
    Binding ordersBinding(
            @Qualifier("ordersQueue") Queue ordersQueue,
            @Qualifier("ordersExchange") DirectExchange ordersExchange,
            OrdersProperties properties) {
        return BindingBuilder.bind(ordersQueue).to(ordersExchange).with(properties.rabbitmq().routingKey());
    }

    @Bean
    Binding ordersDeadLetterBinding(
            @Qualifier("ordersDeadLetterQueue") Queue ordersDeadLetterQueue,
            @Qualifier("ordersDeadLetterExchange") DirectExchange ordersDeadLetterExchange,
            OrdersProperties properties) {
        return BindingBuilder.bind(ordersDeadLetterQueue).to(ordersDeadLetterExchange)
                .with(properties.rabbitmq().deadLetterRoutingKey());
    }
}
