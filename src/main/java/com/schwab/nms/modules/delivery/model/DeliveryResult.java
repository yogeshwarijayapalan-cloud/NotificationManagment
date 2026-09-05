package com.schwab.nms.modules.delivery.model;

import com.schwab.nms.database.entities.enums.FailureType;

public record DeliveryResult(
        boolean success,
        FailureType failureType,
        String failureReason,
        String providerMessageId
) {}