package com.schwab.nms.controller;

import com.schwab.nms.modules.notification.model.NotificationRequest;
import com.schwab.nms.modules.notification.model.NotificationResponse;
import com.schwab.nms.modules.notification.model.NotificationStatusResponse;
import com.schwab.nms.modules.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody NotificationRequest request) {

        log.debug("Submitting notification with idempotency key");
        NotificationResponse response = notificationService.submitNotification(request, idempotencyKey);

        log.info("Notification submitted successfully: notificationId={}", response.notificationId());

        return ResponseEntity
                .accepted()
                .location(URI.create("/api/v1/notifications/" + response.notificationId()))
                .body(response);
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationStatusResponse> getNotificationStatus(
            @PathVariable UUID notificationId) {

        log.debug("Fetching notification status: notificationId={}", notificationId);

        NotificationStatusResponse response = notificationService.getNotificationStatus(notificationId);

        log.debug("Notification status retrieved: notificationId={}", notificationId);
        return ResponseEntity.ok(response);
    }
}