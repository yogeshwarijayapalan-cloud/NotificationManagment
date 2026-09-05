package com.schwab.nms.modules.notification.service;

import com.schwab.nms.controller.exception.IdempotencyConflictException;
import com.schwab.nms.controller.exception.NotificationNotFoundException;
import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.Notification;
import com.schwab.nms.database.entities.Recipient;
import com.schwab.nms.database.entities.RecipientPreference;
import com.schwab.nms.database.entities.enums.AuditEventType;
import com.schwab.nms.database.entities.enums.Channel;
import com.schwab.nms.database.entities.enums.DeliveryStatus;
import com.schwab.nms.database.entities.enums.NotificationStatus;
import com.schwab.nms.database.entities.enums.Priority;
import com.schwab.nms.database.entities.enums.Severity;
import com.schwab.nms.database.repository.DeliveryRepository;
import com.schwab.nms.database.repository.NotificationRepository;
import com.schwab.nms.database.repository.RecipientPreferenceRepository;
import com.schwab.nms.database.repository.RecipientRepository;
import com.schwab.nms.modules.audit.AuditService;
import com.schwab.nms.modules.delivery.model.DeliveryStatusResponse;
import com.schwab.nms.modules.notification.model.NotificationRequest;
import com.schwab.nms.modules.notification.model.NotificationResponse;
import com.schwab.nms.modules.notification.model.NotificationStatusResponse;
import com.schwab.nms.modules.notification.model.RecipientRequest;
import com.schwab.nms.modules.routing.RoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final RecipientRepository recipientRepository;
    private final RecipientPreferenceRepository recipientPreferenceRepository;
    private final DeliveryRepository deliveryRepository;
    private final AuditService auditService;
    private final RoutingService routingService;

    public NotificationService(
            NotificationRepository notificationRepository,
            RecipientRepository recipientRepository,
            RecipientPreferenceRepository recipientPreferenceRepository,
            DeliveryRepository deliveryRepository,
            AuditService auditService,
            RoutingService routingService) {
        this.notificationRepository = notificationRepository;
        this.recipientRepository = recipientRepository;
        this.recipientPreferenceRepository = recipientPreferenceRepository;
        this.deliveryRepository = deliveryRepository;
        this.auditService = auditService;
        this.routingService = routingService;
    }

    @Transactional(noRollbackFor = IdempotencyConflictException.class)
    public NotificationResponse submitNotification(
            NotificationRequest request,
            String idempotencyKey) {

        var existingNotification = notificationRepository.findByIdempotencyKey(idempotencyKey);

        if (existingNotification.isPresent()) {
            Notification existing = existingNotification.get();

            if (!isSameRequest(existing, request)) {
                log.warn("Idempotency conflict: notificationId={}", existing.getId());
                auditService.record(
                        existing,
                        AuditEventType.NOTIFICATION_REJECTED,
                        "Notification rejected: idempotency key reused with a different request");
                throw new IdempotencyConflictException(idempotencyKey);
            }

            log.info("Duplicate notification suppressed: notificationId={}", existing.getId());
            auditService.record(
                    existing,
                    AuditEventType.NOTIFICATION_DUPLICATE_SUPPRESSED,
                    "Duplicate notification submission suppressed");

            return new NotificationResponse(
                    existing.getId(),
                    existing.getStatus().name(),
                    existing.getCreatedAt());
        }

        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setIdempotencyKey(idempotencyKey);
        notification.setSourceSystem(request.sourceSystem());
        notification.setEventId(request.eventId());
        notification.setNotificationType(request.notificationType());
        notification.setSeverity(Severity.valueOf(request.severity().toUpperCase()));
        notification.setPriority(Priority.valueOf(request.priority().toUpperCase()));
        notification.setMessage(request.message());
        notification.setCreatedAt(Instant.now());
        notification.setScheduledAt(request.scheduledAt());
        notification.setExpiresAt(request.expiresAt());
        notification.setStatus(NotificationStatus.ACCEPTED);

        notificationRepository.save(notification);

        log.info("Notification accepted: notificationId={}", notification.getId());

        auditService.record(
                notification,
                AuditEventType.NOTIFICATION_ACCEPTED,
                "Notification accepted");

        for (RecipientRequest recipientRequest : request.recipients()) {
            Recipient recipient = new Recipient();
            recipient.setId(UUID.randomUUID());
            recipient.setNotification(notification);
            recipient.setRecipientKey(recipientRequest.recipientKey());
            recipient.setEmail(recipientRequest.email());
            recipient.setPhone(recipientRequest.phone());

            recipientRepository.save(recipient);

            if (recipientRequest.preferredChannels() != null) {
                for (String channel : recipientRequest.preferredChannels()) {
                    RecipientPreference preference = new RecipientPreference();
                    preference.setId(UUID.randomUUID());
                    preference.setRecipient(recipient);
                    preference.setChannel(Channel.valueOf(channel.toUpperCase()));
                    preference.setEnabled(true);

                    recipientPreferenceRepository.save(preference);
                }
            }
        }

        List<Channel> requestedChannels = request.requestedChannels().stream()
                .map(channel -> Channel.valueOf(channel.toUpperCase()))
                .toList();

        List<Recipient> recipients =
                recipientRepository.findByNotificationId(notification.getId());

        notification.setStatus(NotificationStatus.ROUTING);
        notificationRepository.save(notification);

        auditService.record(
                notification,
                AuditEventType.ROUTING_STARTED,
                "Routing evaluation started");

        for (Recipient recipient : recipients) {
            List<Channel> selectedChannels = routingService.determineChannels(
                    notification,
                    recipient,
                    requestedChannels);

            for (Channel channel : selectedChannels) {
                Delivery delivery = new Delivery();
                delivery.setId(UUID.randomUUID());
                delivery.setNotification(notification);
                delivery.setRecipient(recipient);
                delivery.setChannel(channel);
                delivery.setStatus(DeliveryStatus.QUEUED);
                delivery.setAttemptCount(0);
                delivery.setCreatedAt(Instant.now());

                deliveryRepository.save(delivery);
            }
        }

        List<Delivery> deliveries =
                deliveryRepository.findByNotificationId(notification.getId());

        if (deliveries.isEmpty()) {
            notification.setStatus(NotificationStatus.FAILED);
            notificationRepository.save(notification);

            log.warn("Notification rejected: no eligible delivery channels, notificationId={}",
                    notification.getId());

            auditService.record(
                    notification,
                    AuditEventType.NOTIFICATION_REJECTED,
                    "Notification rejected: no eligible delivery channels");

            return new NotificationResponse(
                    notification.getId(),
                    notification.getStatus().name(),
                    notification.getCreatedAt());
        }

        notification.setStatus(NotificationStatus.QUEUED);
        notificationRepository.save(notification);

        auditService.record(
                notification,
                AuditEventType.NOTIFICATION_QUEUED,
                "Notification deliveries queued");

        log.info("Notification queued: notificationId={}, deliveryCount={}",
                notification.getId(), deliveries.size());

        return new NotificationResponse(
                notification.getId(),
                notification.getStatus().name(),
                notification.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public NotificationStatusResponse getNotificationStatus(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        List<Delivery> deliveries =
                deliveryRepository.findByNotificationId(notificationId);

        List<DeliveryStatusResponse> deliveryResponses = deliveries.stream()
                .map(delivery -> new DeliveryStatusResponse(
                        delivery.getRecipient().getRecipientKey(),
                        delivery.getChannel(),
                        delivery.getStatus(),
                        delivery.getAttemptCount(),
                        delivery.getLastAttemptAt(),
                        delivery.getCompletedAt(),
                        delivery.getFailureType(),
                        delivery.getFailureReason(),
                        delivery.getNextRetryAt()))
                .toList();

        log.debug("Notification status retrieved: notificationId={}, deliveryCount={}",
                notificationId, deliveries.size());

        return new NotificationStatusResponse(
                notification.getId(),
                notification.getSourceSystem(),
                notification.getEventId(),
                notification.getNotificationType(),
                notification.getSeverity(),
                notification.getPriority(),
                notification.getStatus(),
                notification.getCreatedAt(),
                notification.getScheduledAt(),
                notification.getExpiresAt(),
                deliveryResponses);
    }

    private boolean isSameRequest(
            Notification existing,
            NotificationRequest request) {
        return existing.getSourceSystem().equals(request.sourceSystem())
                && existing.getEventId().equals(request.eventId())
                && existing.getNotificationType().equals(request.notificationType())
                && existing.getSeverity().name().equalsIgnoreCase(request.severity())
                && existing.getPriority().name().equalsIgnoreCase(request.priority())
                && existing.getMessage().equals(request.message())
                && sameInstant(existing.getScheduledAt(), request.scheduledAt())
                && sameInstant(existing.getExpiresAt(), request.expiresAt());
    }

    private boolean sameInstant(Instant first, Instant second) {
        return Objects.equals(first, second);
    }
}