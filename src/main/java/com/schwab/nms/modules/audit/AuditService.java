package com.schwab.nms.modules.audit;

import com.schwab.nms.database.entities.AuditEvent;
import com.schwab.nms.database.entities.Notification;
import com.schwab.nms.database.entities.enums.AuditEventType;
import com.schwab.nms.database.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public void record(Notification notification, AuditEventType eventType, String details) {
        UUID auditEventId = UUID.randomUUID();
        log.debug("Recording audit event: auditEventId={}, eventType={}", auditEventId, eventType);

        AuditEvent event = new AuditEvent();
        event.setId(auditEventId);
        event.setNotification(notification);
        event.setEventType(eventType);
        event.setEventTime(Instant.now());
        event.setDetails(details);

        try {
            auditEventRepository.save(event);
            log.debug("Audit event recorded: auditEventId={}, eventType={}", auditEventId, eventType);
        } catch (RuntimeException ex) {
            log.error("Failed to record audit event: auditEventId={}, eventType={}", auditEventId, eventType, ex);
            throw ex;
        }
    }
}