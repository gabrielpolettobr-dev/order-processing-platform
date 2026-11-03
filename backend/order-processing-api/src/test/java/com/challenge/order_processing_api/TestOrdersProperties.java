package com.challenge.order_processing_api;

import com.challenge.order_processing_api.config.OrdersProperties;
import java.time.Duration;

public final class TestOrdersProperties {

    private TestOrdersProperties() {
    }

    public static OrdersProperties defaults() {
        return new OrdersProperties(
                new OrdersProperties.Processing(Duration.ofSeconds(1), Duration.ofMinutes(5), ""),
                new OrdersProperties.Pagination(100),
                new OrdersProperties.Outbox(1_000, Duration.ofSeconds(30)),
                new OrdersProperties.RabbitMq(
                        "orders.exchange",
                        "orders.queue",
                        "orders.created",
                        "orders.dlx",
                        "orders.dlq",
                        "orders.failed",
                        new OrdersProperties.Retry(3, 1_000, 2.0, 10_000)));
    }
}
