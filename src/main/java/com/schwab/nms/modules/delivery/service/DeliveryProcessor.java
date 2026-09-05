package com.schwab.nms.modules.delivery.service;

import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.enums.AuditEventType;
import com.schwab.nms.database.entities.enums.Channel;
import com.schwab.nms.database.entities.enums.DeliveryStatus;
import com.schwab.nms.database.entities.enums.FailureType;
import com.schwab.nms.database.repository.DeliveryRepository;
import com.schwab.nms.modules.audit.AuditService;
import com.schwab.nms.modules.delivery.model.DeliveryResult;
import com.schwab.nms.modules.provider.NotificationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DeliveryProcessor {

    private static final Logger log = LoggerFactory.getLogger(DeliveryProcessor.class);

    private final NotificationStatusService notificationStatusService;
    private final DeliveryRepository deliveryRepository;
    private final Map<Channel, NotificationProvider> providers;
    private final AuditService auditService;
    private final DeliveryFailureHandler failureHandler;

    public DeliveryProcessor(
            DeliveryRepository deliveryRepository,
            NotificationStatusService notificationStatusService,
            List<NotificationProvider> notificationProviders,
            AuditService auditService,
            DeliveryFailureHandler failureHandler) {
        this.deliveryRepository = deliveryRepository;
        this.notificationStatusService = notificationStatusService;
        this.auditService = auditService;
        this.failureHandler = failureHandler;
        this.providers = notificationProviders.stream()
                .collect(Collectors.toMap(NotificationProvider::getChannel, Function.identity()));
    }

    @Transactional
    @Scheduled(fixedDelay = 5000)
    public void processQueuedDeliveries() {
        List<Delivery> queued = deliveryRepository.findByStatus(DeliveryStatus.QUEUED);
        List<Delivery> retries = deliveryRepository.findByStatus(DeliveryStatus.RETRY_PENDING).stream()
                .filter(this::isRetryReady)
                .toList();

        if (!queued.isEmpty() || !retries.isEmpty()) {
            log.debug("Processing deliveries: queued={}, retries={}", queued.size(), retries.size());
        }

        processDeliveries(queued);
        processDeliveries(retries);
    }

    private void processDeliveries(List<Delivery> deliveries) {
        for (Delivery delivery : deliveries) {
            process(delivery);
        }
    }

    private boolean isRetryReady(Delivery delivery) {
        return delivery.getNextRetryAt() == null
                || !delivery.getNextRetryAt().isAfter(Instant.now());
    }

    private void process(Delivery delivery) {
        if (isExpired(delivery)) {
            log.info("Delivery expired: deliveryId={}", delivery.getId());
            expireNotification(delivery);
            return;
        }

        if (!isScheduled(delivery)) {
            return;
        }

        NotificationProvider provider = providers.get(delivery.getChannel());

        if (provider == null) {
            log.error("No provider configured: deliveryId={}, channel={}",
                    delivery.getId(), delivery.getChannel());
            failureHandler.failDelivery(
                    delivery,
                    FailureType.PERMANENT,
                    "No provider configured for channel " + delivery.getChannel());
            return;
        }

        try {
            markInProgress(delivery);
            log.debug("Sending delivery: deliveryId={}, channel={}, attempt={}",
                    delivery.getId(), delivery.getChannel(), delivery.getAttemptCount());

            DeliveryResult result = provider.send(delivery);

            if (result.success()) {
                handleSuccess(delivery, result);
            } else {
                failureHandler.handle(delivery, result);
            }
        } catch (Exception ex) {
            log.error("Unexpected error processing delivery: deliveryId={}, channel={}",
                    delivery.getId(), delivery.getChannel(), ex);

            failureHandler.failDelivery(
                    delivery,
                    FailureType.TRANSIENT,
                    "Unexpected processor error: " + ex.getMessage());
        }
    }

    private boolean isScheduled(Delivery delivery) {
        Instant scheduledAt = delivery.getNotification().getScheduledAt();
        return scheduledAt == null || !scheduledAt.isAfter(Instant.now());
    }

    private boolean isExpired(Delivery delivery) {
        Instant expiresAt = delivery.getNotification().getExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }

    private void expireNotification(Delivery delivery) {
        delivery.setStatus(DeliveryStatus.EXPIRED);
        delivery.setCompletedAt(Instant.now());
        delivery.setNextRetryAt(null);
        deliveryRepository.save(delivery);

        notificationStatusService.markExpired(delivery.getNotification());

        auditService.record(
                delivery.getNotification(),
                AuditEventType.NOTIFICATION_EXPIRED,
                "Notification expired before delivery");

        log.info("Notification marked expired: deliveryId={}", delivery.getId());
    }

    private void markInProgress(Delivery delivery) {
        delivery.setStatus(DeliveryStatus.IN_PROGRESS);
        delivery.setLastAttemptAt(Instant.now());
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        deliveryRepository.save(delivery);

        auditService.record(
                delivery.getNotification(),
                AuditEventType.DELIVERY_ATTEMPTED,
                "Attempt " + delivery.getAttemptCount());
    }

    private void handleSuccess(Delivery delivery, DeliveryResult result) {
        delivery.setStatus(DeliveryStatus.DELIVERED);
        delivery.setProviderMessageId(result.providerMessageId());
        delivery.setCompletedAt(Instant.now());
        delivery.setFailureType(null);
        delivery.setFailureReason(null);
        delivery.setNextRetryAt(null);
        deliveryRepository.save(delivery);

        auditService.record(
                delivery.getNotification(),
                AuditEventType.DELIVERY_SUCCEEDED,
                "Channel=" + delivery.getChannel());

        notificationStatusService.update(delivery);

        log.info("Delivery succeeded: deliveryId={}, channel={}, attempt={}",
                delivery.getId(), delivery.getChannel(), delivery.getAttemptCount());
    }
}