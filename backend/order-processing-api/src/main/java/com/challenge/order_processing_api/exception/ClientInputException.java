package com.challenge.order_processing_api.exception;

public abstract class ClientInputException extends RuntimeException {

    protected ClientInputException(String message) {
        super(message);
    }

    protected ClientInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
