package com.challenge.order_processing_api.exception;

public class InvalidOrderQueryException extends ClientInputException {

    public InvalidOrderQueryException(String message) {
        super(message);
    }
}
