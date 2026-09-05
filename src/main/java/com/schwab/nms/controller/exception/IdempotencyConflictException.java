package com.schwab.nms.controller.exception;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key already exists with a different request: "
                + idempotencyKey);
    }
}