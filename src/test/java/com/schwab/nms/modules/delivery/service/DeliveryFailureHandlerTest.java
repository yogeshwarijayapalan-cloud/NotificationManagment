package com.schwab.nms.modules.delivery.service;

import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.Notification;
import com.schwab.nms.database.entities.enums.DeliveryStatus;
import com.schwab.nms.database.entities.enums.FailureType;
import com.schwab.nms.database.repository.DeliveryRepository;
import com.schwab.nms.modules.audit.AuditService;
import com.schwab.nms.modules.delivery.model.DeliveryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryFailureHandlerTest {
    private DeliveryRepository deliveryRepository;
    private AuditService auditService;
    private NotificationStatusService notificationStatusService;
    private DeliveryFailureHandler failureHandler;

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(DeliveryRepository.class);
        auditService = mock(AuditService.class);
        notificationStatusService = mock(NotificationStatusService.class);
        failureHandler = new DeliveryFailureHandler(
                deliveryRepository, auditService, notificationStatusService);
    }

    @Test
    void shouldScheduleRetryForTransientFailure() {
        Delivery delivery = createDelivery();
        delivery.setAttemptCount(1);

        DeliveryResult result = new DeliveryResult(
                false, FailureType.TRANSIENT, "Temporary provider error", null);

        failureHandler.handle(delivery, result);

        assertEquals(DeliveryStatus.RETRY_PENDING, delivery.getStatus());
        assertNotNull(delivery.getNextRetryAt());
        assertEquals(FailureType.TRANSIENT, delivery.getFailureType());
        verify(deliveryRepository).save(delivery);
        verify(auditService).record(any(), any(), contains("Retry attempt"));
    }

    @Test
    void shouldNotRetryPermanentFailure() {
        Delivery delivery = createDelivery();
        delivery.setAttemptCount(1);

        DeliveryResult result = new DeliveryResult(
                false, FailureType.PERMANENT, "Invalid request", null);

        failureHandler.handle(delivery, result);

        assertEquals(DeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(FailureType.PERMANENT, delivery.getFailureType());
        assertNull(delivery.getNextRetryAt());
        assertNotNull(delivery.getCompletedAt());
        verify(deliveryRepository).save(delivery);
        verify(auditService).record(any(), any(), contains("Non-retryable"));
        verify(notificationStatusService).update(delivery);
    }

    @Test
    void shouldFailAfterMaximumAttempts() {
        Delivery delivery = createDelivery();
        delivery.setAttemptCount(3);

        DeliveryResult result = new DeliveryResult(
                false, FailureType.TIMEOUT, "Provider timeout", null);

        failureHandler.handle(delivery, result);

        assertEquals(DeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(FailureType.TIMEOUT, delivery.getFailureType());
        assertNull(delivery.getNextRetryAt());
        assertNotNull(delivery.getCompletedAt());
        verify(deliveryRepository).save(delivery);
        verify(notificationStatusService).update(delivery);
    }

    @Test
    void shouldRetryRateLimitedFailure() {
        Delivery delivery = createDelivery();
        delivery.setAttemptCount(1);

        DeliveryResult result = new DeliveryResult(
                false, FailureType.RATE_LIMITED, "Rate limit exceeded", null);

        failureHandler.handle(delivery, result);

        assertEquals(DeliveryStatus.RETRY_PENDING, delivery.getStatus());
        assertNotNull(delivery.getNextRetryAt());
    }

    private Delivery createDelivery() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());

        Delivery delivery = new Delivery();
        delivery.setId(UUID.randomUUID());
        delivery.setNotification(notification);
        delivery.setAttemptCount(0);
        delivery.setStatus(DeliveryStatus.QUEUED);
        return delivery;
    }
}