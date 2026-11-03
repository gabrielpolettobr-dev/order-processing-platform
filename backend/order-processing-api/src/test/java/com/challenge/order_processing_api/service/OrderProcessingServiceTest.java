package com.challenge.order_processing_api.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        processingService = new OrderProcessingService(stateService, Duration.ZERO, "");
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
        when(stateService.hasItems(orderId)).thenThrow(new IllegalStateException("temporary failure"));

        assertThatThrownBy(() -> processingService.process(orderId))
                .isInstanceOf(IllegalStateException.class);

        verify(stateService).markReadyForRetry(orderId);
        verify(stateService, never()).markCompleted(orderId);
    }
}
