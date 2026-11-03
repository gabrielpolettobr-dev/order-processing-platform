package com.challenge.order_processing_api.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        String description,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {
}
