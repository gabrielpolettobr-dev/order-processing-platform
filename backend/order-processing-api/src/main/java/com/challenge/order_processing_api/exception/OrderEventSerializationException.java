package com.challenge.order_processing_api.exception;

public class OrderEventSerializationException extends RuntimeException {

    public OrderEventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
