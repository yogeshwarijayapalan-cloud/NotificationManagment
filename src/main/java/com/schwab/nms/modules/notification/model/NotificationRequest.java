package com.schwab.nms.modules.notification.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public record NotificationRequest(

        @NotBlank
        String sourceSystem,

        @NotBlank
        String eventId,

        @NotBlank
        String notificationType,

        @NotBlank
        String severity,

        @NotBlank
        String priority,

        @NotBlank
        String message,

        @NotEmpty
        List<@Valid RecipientRequest> recipients,

        @NotEmpty
        List<String> requestedChannels,

        Instant scheduledAt,

        Instant expiresAt
) {
}