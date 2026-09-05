package com.schwab.nms.modules.notification.model;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        String status,
        Instant createdAt
) {
}