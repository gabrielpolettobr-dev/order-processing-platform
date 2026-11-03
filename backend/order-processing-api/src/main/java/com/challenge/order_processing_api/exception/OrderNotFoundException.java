package com.challenge.order_processing_api.exception;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID id) {
        super("Pedido não encontrado: " + id);
    }
}
