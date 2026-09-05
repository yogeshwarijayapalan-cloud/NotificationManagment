package com.schwab.nms.modules.delivery.service;

import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.Notification;
import com.schwab.nms.database.entities.enums.DeliveryStatus;
import com.schwab.nms.database.entities.enums.NotificationStatus;
import com.schwab.nms.database.repository.DeliveryRepository;
import com.schwab.nms.database.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationStatusService {

    private static final Logger log = LoggerFactory.getLogger(NotificationStatusService.class);

    private final DeliveryRepository deliveryRepository;
    private final NotificationRepository notificationRepository;

    public NotificationStatusService(
            DeliveryRepository deliveryRepository,
            NotificationRepository notificationRepository) {
        this.deliveryRepository = deliveryRepository;
        this.notificationRepository = notificationRepository;
    }

    public void update(Delivery delivery) {
        List<Delivery> deliveries = deliveryRepository.findByNotificationId(
                delivery.getNotification().getId());

        NotificationStatus status = determineStatus(deliveries);

        Notification notification = delivery.getNotification();
        notification.setStatus(status);
        notificationRepository.save(notification);

        log.debug("Notification status updated: notificationId={}, status={}",
                notification.getId(), status);
    }

    public void markExpired(Notification notification) {
        notification.setStatus(NotificationStatus.EXPIRED);
        notificationRepository.save(notification);

        log.info("Notification marked expired: notificationId={}", notification.getId());
    }

    private NotificationStatus determineStatus(List<Delivery> deliveries) {
        boolean allDelivered = deliveries.stream().allMatch(this::isDelivered);
        boolean anyDelivered = deliveries.stream().anyMatch(this::isDelivered);
        boolean anyFailed = deliveries.stream().anyMatch(this::isFailed);
        boolean anyPending = deliveries.stream().anyMatch(this::isPending);

        if (allDelivered) {
            return NotificationStatus.DELIVERED;
        }
        if (anyPending) {
            return NotificationStatus.PROCESSING;
        }
        if (anyDelivered && anyFailed) {
            return NotificationStatus.PARTIALLY_DELIVERED;
        }
        if (anyFailed) {
            return NotificationStatus.FAILED;
        }

        return NotificationStatus.PROCESSING;
    }

    private boolean isDelivered(Delivery delivery) {
        return delivery.getStatus() == DeliveryStatus.DELIVERED;
    }

    private boolean isFailed(Delivery delivery) {
        return delivery.getStatus() == DeliveryStatus.FAILED;
    }

    private boolean isPending(Delivery delivery) {
        return delivery.getStatus() == DeliveryStatus.QUEUED
                || delivery.getStatus() == DeliveryStatus.IN_PROGRESS
                || delivery.getStatus() == DeliveryStatus.RETRY_PENDING;
    }
}