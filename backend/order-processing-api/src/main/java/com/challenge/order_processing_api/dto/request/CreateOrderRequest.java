package com.challenge.order_processing_api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank
        @Size(max = 200)
        String customerName,

        @NotBlank
        @Email
        @Size(max = 320)
        String customerEmail,

        @NotEmpty
        @Valid
        List<CreateOrderItemRequest> items
) {
}
