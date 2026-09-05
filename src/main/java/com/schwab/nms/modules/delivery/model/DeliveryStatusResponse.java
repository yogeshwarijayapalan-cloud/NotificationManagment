package com.schwab.nms.modules.delivery.model;

import com.schwab.nms.database.entities.enums.Channel;
import com.schwab.nms.database.entities.enums.DeliveryStatus;
import com.schwab.nms.database.entities.enums.FailureType;

import java.time.Instant;

public record DeliveryStatusResponse(
        String recipientKey,
        Channel channel,
        DeliveryStatus status,
        int attemptCount,
        Instant lastAttemptAt,
        Instant completedAt,
        FailureType failureType,
        String failureReason,
        Instant nextRetryAt
) {
}