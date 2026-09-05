package com.schwab.nms.modules.delivery.service;

import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.Notification;
import com.schwab.nms.database.entities.enums.DeliveryStatus;
import com.schwab.nms.database.entities.enums.NotificationStatus;
import com.schwab.nms.database.repository.DeliveryRepository;
import com.schwab.nms.database.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class NotificationStatusServiceTest {
    private DeliveryRepository deliveryRepository;
    private NotificationRepository notificationRepository;
    private NotificationStatusService statusService;

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(DeliveryRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        statusService = new NotificationStatusService(
                deliveryRepository, notificationRepository);
    }

    @Test
    void shouldMarkDeliveredWhenAllDeliveriesSucceed() {
        Delivery delivery1 = delivery(DeliveryStatus.DELIVERED);
        Delivery delivery2 = delivery(DeliveryStatus.DELIVERED);

        Notification notification = delivery1.getNotification();
        when(deliveryRepository.findByNotificationId(notification.getId()))
                .thenReturn(List.of(delivery1, delivery2));

        statusService.update(delivery1);

        assertEquals(NotificationStatus.DELIVERED, notification.getStatus());
        verify(notificationRepository).save(notification);
    }

    @Test
    void shouldMarkPartiallyDeliveredWhenSomeDeliveriesFail() {
        Delivery delivery1 = delivery(DeliveryStatus.DELIVERED);
        Delivery delivery2 = delivery(DeliveryStatus.FAILED);

        Notification notification = delivery1.getNotification();
        when(deliveryRepository.findByNotificationId(notification.getId()))
                .thenReturn(List.of(delivery1, delivery2));

        statusService.update(delivery1);

        assertEquals(NotificationStatus.PARTIALLY_DELIVERED, notification.getStatus());
    }

    @Test
    void shouldMarkFailedWhenAllDeliveriesFail() {
        Delivery delivery1 = delivery(DeliveryStatus.FAILED);
        Delivery delivery2 = delivery(DeliveryStatus.FAILED);

        Notification notification = delivery1.getNotification();
        when(deliveryRepository.findByNotificationId(notification.getId()))
                .thenReturn(List.of(delivery1, delivery2));

        statusService.update(delivery1);

        assertEquals(NotificationStatus.FAILED, notification.getStatus());
    }

    @Test
    void shouldRemainProcessingWhenDeliveryIsPending() {
        Delivery delivery1 = delivery(DeliveryStatus.DELIVERED);
        Delivery delivery2 = delivery(DeliveryStatus.RETRY_PENDING);

        Notification notification = delivery1.getNotification();
        when(deliveryRepository.findByNotificationId(notification.getId()))
                .thenReturn(List.of(delivery1, delivery2));

        statusService.update(delivery1);

        assertEquals(NotificationStatus.PROCESSING, notification.getStatus());
    }

    @Test
    void shouldMarkNotificationAsExpired() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setStatus(NotificationStatus.QUEUED);

        statusService.markExpired(notification);

        assertEquals(NotificationStatus.EXPIRED, notification.getStatus());
        verify(notificationRepository).save(notification);
    }

    private Delivery delivery(DeliveryStatus status) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());

        Delivery delivery = new Delivery();
        delivery.setId(UUID.randomUUID());
        delivery.setNotification(notification);
        delivery.setStatus(status);
        return delivery;
    }
}