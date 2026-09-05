package com.schwab.nms.bdd;

import com.schwab.nms.database.repository.AuditEventRepository;
import com.schwab.nms.database.repository.DeliveryRepository;
import com.schwab.nms.database.repository.NotificationRepository;
import com.schwab.nms.database.repository.RecipientPreferenceRepository;
import com.schwab.nms.database.repository.RecipientRepository;
import io.cucumber.java.After;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TestDatabaseHooks {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private RecipientPreferenceRepository recipientPreferenceRepository;

    @Autowired
    private RecipientRepository recipientRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private final Set<UUID> createdNotificationIds = new HashSet<>();

    public void trackNotification(UUID notificationId) {
        createdNotificationIds.add(notificationId);
    }

    @After
    @Transactional
    public void cleanUpCreatedNotifications() {

        for (UUID notificationId : createdNotificationIds) {
            if(notificationId == null) {
                continue;
            }

            auditEventRepository.deleteByNotification_Id(notificationId);
            deliveryRepository.deleteByNotification_Id(notificationId);
            recipientPreferenceRepository.deleteByRecipient_Notification_Id(notificationId);
            recipientRepository.deleteByNotification_Id(notificationId);
            notificationRepository.deleteById(notificationId);
        }

        createdNotificationIds.clear();
    }
}