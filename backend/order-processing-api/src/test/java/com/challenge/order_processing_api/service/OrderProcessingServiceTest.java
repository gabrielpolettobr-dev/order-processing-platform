package com.challenge.order_processing_api.service;

import static com.challenge.order_processing_api.TestOrdersProperties.defaults;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.challenge.order_processing_api.config.OrdersProperties;
import com.challenge.order_processing_api.exception.OrderProcessingException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderProcessingServiceTest {

    @Mock
    private OrderProcessingStateService stateService;

    private OrderProcessingService processingService;

    @BeforeEach
    void setUp() {
        OrdersProperties baseProperties = defaults();
        OrdersProperties.Processing processingProperties = new OrdersProperties.Processing(
                Duration.ZERO,
                baseProperties.processing().lockTimeout(),
                baseProperties.processing().failItemDescriptionPrefix());
        OrdersProperties properties = new OrdersProperties(
                processingProperties,
                baseProperties.pagination(),
                baseProperties.outbox(),
                baseProperties.rabbitmq());
        processingService = new OrderProcessingService(stateService, properties);
    }

    @Test
    void completesOrderAfterSuccessfulProcessing() {
        UUID orderId = UUID.randomUUID();
        when(stateService.markProcessing(orderId)).thenReturn(true);
        when(stateService.hasItems(orderId)).thenReturn(true);

        processingService.process(orderId);

        verify(stateService).markCompleted(orderId);
    }

    @Test
    void ignoresDuplicateDeliveryForAnAlreadyClaimedOrder() {
        UUID orderId = UUID.randomUUID();
        when(stateService.markProcessing(orderId)).thenReturn(false);

        processingService.process(orderId);

        verify(stateService, never()).hasItems(orderId);
        verify(stateService, never()).markCompleted(orderId);
    }

    @Test
    void releasesOrderForRetryWhenProcessingFails() {
        UUID orderId = UUID.randomUUID();
        when(stateService.markProcessing(orderId)).thenReturn(true);
        when(stateService.hasItems(orderId)).thenThrow(new OrderProcessingException("temporary failure"));

        assertThatThrownBy(() -> processingService.process(orderId))
                .isInstanceOf(OrderProcessingException.class);

        verify(stateService).markReadyForRetry(orderId);
        verify(stateService, never()).markCompleted(orderId);
    }
}
