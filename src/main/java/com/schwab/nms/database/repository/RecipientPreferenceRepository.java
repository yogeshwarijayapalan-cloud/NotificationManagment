package com.schwab.nms.database.repository;

import com.schwab.nms.database.entities.RecipientPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecipientPreferenceRepository
        extends JpaRepository<RecipientPreference, UUID> {

    List<RecipientPreference> findByRecipientId(UUID recipientId);

    void deleteByRecipient_Notification_Id(UUID notificationId);
}