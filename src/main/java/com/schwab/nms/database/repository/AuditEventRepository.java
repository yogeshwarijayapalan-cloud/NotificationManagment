package com.schwab.nms.database.repository;

import com.schwab.nms.database.entities.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository
        extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByNotificationIdOrderByEventTimeAsc(
            UUID notificationId
    );

    void deleteByNotification_Id(UUID notificationId);

}