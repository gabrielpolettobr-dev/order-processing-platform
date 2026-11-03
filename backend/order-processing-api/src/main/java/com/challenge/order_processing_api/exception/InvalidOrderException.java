package com.challenge.order_processing_api.exception;

public class InvalidOrderException extends ClientInputException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
