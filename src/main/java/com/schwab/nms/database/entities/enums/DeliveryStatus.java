package com.schwab.nms.database.entities.enums;

public enum DeliveryStatus {
    QUEUED,
    IN_PROGRESS,
    DELIVERED,
    RETRY_PENDING,
    FAILED,
    EXPIRED
}