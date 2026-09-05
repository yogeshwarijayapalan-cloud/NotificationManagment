package com.schwab.nms.database.entities.enums;

public enum FailureType {
    TRANSIENT,
    PERMANENT,
    INVALID_RECIPIENT,
    RATE_LIMITED,
    TIMEOUT,
    AUTHORIZATION_ERROR
}