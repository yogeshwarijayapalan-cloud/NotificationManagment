package com.schwab.nms.database.repository;

import com.schwab.nms.database.entities.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecipientRepository
        extends JpaRepository<Recipient, UUID> {

    List<Recipient> findByNotificationId(UUID notificationId);

    void deleteByNotification_Id(UUID notificationId);
}