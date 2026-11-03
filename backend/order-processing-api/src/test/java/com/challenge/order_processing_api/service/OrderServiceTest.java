package com.challenge.order_processing_api.service;

import static com.challenge.order_processing_api.TestOrdersProperties.defaults;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.challenge.order_processing_api.dto.request.CreateOrderItemRequest;
import com.challenge.order_processing_api.dto.request.CreateOrderRequest;
import com.challenge.order_processing_api.exception.InvalidOrderQueryException;
import com.challenge.order_processing_api.model.Order;
import com.challenge.order_processing_api.model.OutboxEvent;
import com.challenge.order_processing_api.repository.OrderRepository;
import com.challenge.order_processing_api.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, outboxEventRepository, objectMapper, defaults());
    }

    @Test
    void savesOrderAndOutboxEventWithCalculatedTotal() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateOrderRequest request = new CreateOrderRequest("Maria Silva", "maria@example.com", List.of(
                new CreateOrderItemRequest("Teclado", 2, new BigDecimal("150.00")),
                new CreateOrderItemRequest("Mouse", 1, new BigDecimal("80.00"))));

        var response = orderService.create(request);

        assertThat(response.totalAmount()).isEqualByComparingTo("380.00");
        assertThat(response.status().name()).isEqualTo("RECEIVED");
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getAggregateId()).isEqualTo(response.id());
        assertThat(outboxCaptor.getValue().getPayload()).contains(response.id().toString());
    }

    @Test
    void rejectsPageSizesAboveConfiguredLimit() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        orderService.findAll(null, null, null, PageRequest.of(0, 101)))
                .isInstanceOf(InvalidOrderQueryException.class)
                .hasMessageContaining("tamanho da página");

        verifyNoInteractions(orderRepository);
    }

    @Test
    void rejectsUnsupportedSortProperties() {
        String unsupportedProperty = "items.description";

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        orderService.findAll(null, null, null,
                                PageRequest.of(0, 20, Sort.by(unsupportedProperty))))
                .isInstanceOf(InvalidOrderQueryException.class)
                .hasMessage("Campo de ordenação não permitido.")
                .hasMessageNotContaining(unsupportedProperty);

        verifyNoInteractions(orderRepository);
    }

    @Test
    void rejectsInvalidCreationPeriod() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        orderService.findAll(
                                null,
                                java.time.Instant.parse("2026-11-03T00:00:00Z"),
                                java.time.Instant.parse("2026-11-02T00:00:00Z"),
                                PageRequest.of(0, 20)))
                .isInstanceOf(InvalidOrderQueryException.class)
                .hasMessageContaining("createdFrom");

        verifyNoInteractions(orderRepository);
    }
}
