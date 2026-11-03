package com.challenge.order_processing_api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "orders")
public record OrdersProperties(
        @Valid @NotNull Processing processing,
        @Valid @NotNull Pagination pagination,
        @Valid @NotNull Outbox outbox,
        @Valid @NotNull RabbitMq rabbitmq) {

    public record Processing(
            @NotNull Duration delay,
            @NotNull Duration lockTimeout,
            @NotNull String failItemDescriptionPrefix) {
    }

    public record Pagination(@Min(1) int maxPageSize) {
    }

    public record Outbox(@Min(1) long pollIntervalMs, @NotNull Duration claimTimeout) {
    }

    public record RabbitMq(
            @NotBlank String exchange,
            @NotBlank String queue,
            @NotBlank String routingKey,
            @NotBlank String deadLetterExchange,
            @NotBlank String deadLetterQueue,
            @NotBlank String deadLetterRoutingKey,
            @Valid @NotNull Retry retry) {
    }

    public record Retry(
            @Min(1) int maxAttempts,
            @Min(1) long initialIntervalMs,
            @DecimalMin("1.0") double multiplier,
            @Min(1) long maxIntervalMs) {
    }
}
