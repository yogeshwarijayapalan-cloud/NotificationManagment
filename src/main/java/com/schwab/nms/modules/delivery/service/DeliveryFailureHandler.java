package com.schwab.nms.modules.delivery.service;

import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.enums.AuditEventType;
import com.schwab.nms.database.entities.enums.DeliveryStatus;
import com.schwab.nms.database.entities.enums.FailureType;
import com.schwab.nms.database.repository.DeliveryRepository;
import com.schwab.nms.modules.audit.AuditService;
import com.schwab.nms.modules.delivery.model.DeliveryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class DeliveryFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryFailureHandler.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BASE_DELAY_SECONDS = 5;

    private final DeliveryRepository deliveryRepository;
    private final AuditService auditService;
    private final NotificationStatusService notificationStatusService;

    public DeliveryFailureHandler(
            DeliveryRepository deliveryRepository,
            AuditService auditService,
            NotificationStatusService notificationStatusService) {
        this.deliveryRepository = deliveryRepository;
        this.auditService = auditService;
        this.notificationStatusService = notificationStatusService;
    }

    public void handle(Delivery delivery, DeliveryResult result) {
        delivery.setFailureType(result.failureType());
        delivery.setFailureReason(result.failureReason());

        log.info("Delivery failed: deliveryId={}, failureType={}, attemptCount={}",
                delivery.getId(), result.failureType(), delivery.getAttemptCount());

        if (!isRetryable(result.failureType())) {
            failDelivery(delivery, result.failureType(),
                    "Non-retryable failure: " + result.failureType());
            return;
        }

        if (delivery.getAttemptCount() >= MAX_ATTEMPTS) {
            failDelivery(delivery, result.failureType(),
                    "Maximum attempts exhausted: " + delivery.getAttemptCount());
            return;
        }

        scheduleRetry(delivery);
    }

    public void failDelivery(Delivery delivery, FailureType failureType, String reason) {
        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setFailureType(failureType);
        delivery.setFailureReason(reason);
        delivery.setNextRetryAt(null);
        delivery.setCompletedAt(Instant.now());

        deliveryRepository.save(delivery);

        auditService.record(
                delivery.getNotification(),
                AuditEventType.DELIVERY_FAILED,
                reason
        );

        notificationStatusService.update(delivery);

        log.info("Delivery permanently failed: deliveryId={}, failureType={}, reason={}",
                delivery.getId(), failureType, reason);
    }

    private boolean isRetryable(FailureType failureType) {
        return failureType == FailureType.TRANSIENT
                || failureType == FailureType.RATE_LIMITED
                || failureType == FailureType.TIMEOUT;
    }

    private void scheduleRetry(Delivery delivery) {
        long delaySeconds = RETRY_BASE_DELAY_SECONDS * (1L << delivery.getAttemptCount());
        Instant nextRetryAt = Instant.now().plusSeconds(delaySeconds);

        delivery.setStatus(DeliveryStatus.RETRY_PENDING);
        delivery.setNextRetryAt(nextRetryAt);

        deliveryRepository.save(delivery);

        auditService.record(
                delivery.getNotification(),
                AuditEventType.RETRY_SCHEDULED,
                "Retry attempt " + (delivery.getAttemptCount() + 1) + " scheduled at " + nextRetryAt
        );

        log.info("Delivery retry scheduled: deliveryId={}, attempt={}, nextRetryAt={}",
                delivery.getId(), delivery.getAttemptCount() + 1, nextRetryAt);
    }
}