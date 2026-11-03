package com.challenge.order_processing_api.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void calculatesItemSubtotalsAndOrderTotalOnTheBackend() {
        Order order = new Order("Maria Silva", "maria@example.com", List.of(
                new OrderItem("Teclado", 2, new BigDecimal("150.00")),
                new OrderItem("Mouse", 1, new BigDecimal("80.00"))));

        assertThat(order.getTotalAmount()).isEqualByComparingTo("380.00");
        assertThat(order.getItems()).extracting(OrderItem::getSubtotal)
                .containsExactly(new BigDecimal("300.00"), new BigDecimal("80.00"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECEIVED);
    }
}
