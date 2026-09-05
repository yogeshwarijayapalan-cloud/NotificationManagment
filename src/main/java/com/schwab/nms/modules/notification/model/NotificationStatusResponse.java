package com.schwab.nms.modules.notification.model;

import com.schwab.nms.database.entities.enums.NotificationStatus;
import com.schwab.nms.database.entities.enums.Priority;
import com.schwab.nms.database.entities.enums.Severity;
import com.schwab.nms.modules.delivery.model.DeliveryStatusResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationStatusResponse(
        UUID notificationId,
        String sourceSystem,
        String eventId,
        String notificationType,
        Severity severity,
        Priority priority,
        NotificationStatus status,
        Instant createdAt,
        Instant scheduledAt,
        Instant expiresAt,
        List<DeliveryStatusResponse> deliveries
) {
}