package com.schwab.nms.database.repository;

import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryRepository
        extends JpaRepository<Delivery, UUID> {

    List<Delivery> findByNotificationId(UUID notificationId);

    @EntityGraph(attributePaths = {"recipient", "notification"})
    List<Delivery> findByStatus(DeliveryStatus status);

    void deleteByNotification_Id(UUID notificationId);
}