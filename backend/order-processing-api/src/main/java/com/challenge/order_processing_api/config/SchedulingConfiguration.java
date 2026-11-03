package com.challenge.order_processing_api.config;

import com.challenge.order_processing_api.messaging.OutboxPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
public class SchedulingConfiguration implements SchedulingConfigurer {

    private final OutboxPublisher outboxPublisher;
    private final OrdersProperties ordersProperties;

    public SchedulingConfiguration(OutboxPublisher outboxPublisher, OrdersProperties ordersProperties) {
        this.outboxPublisher = outboxPublisher;
        this.ordersProperties = ordersProperties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(
                outboxPublisher::publishPendingEvents,
                ordersProperties.outbox().pollIntervalMs());
    }
}
