CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    source_system VARCHAR(100) NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(30) NOT NULL
);

CREATE TABLE recipients (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    recipient_key VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),

    CONSTRAINT fk_recipient_notification
        FOREIGN KEY (notification_id)
        REFERENCES notifications(id)
);


CREATE TABLE recipient_preferences (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_preference_recipient
        FOREIGN KEY (recipient_id)
        REFERENCES recipients(id),

    CONSTRAINT uq_recipient_channel
        UNIQUE (recipient_id, channel)
);


CREATE TABLE deliveries (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    provider_message_id VARCHAR(200),
    failure_type VARCHAR(40),
    failure_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_delivery_notification
        FOREIGN KEY (notification_id)
        REFERENCES notifications(id),

    CONSTRAINT fk_delivery_recipient
        FOREIGN KEY (recipient_id)
        REFERENCES recipients(id),

    CONSTRAINT uq_notification_recipient_channel
        UNIQUE (notification_id, recipient_id, channel)
);


CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_time TIMESTAMP WITH TIME ZONE NOT NULL,
    details TEXT,

    CONSTRAINT fk_audit_notification
        FOREIGN KEY (notification_id)
        REFERENCES notifications(id)
);

CREATE INDEX idx_recipients_notification_id
    ON recipients(notification_id);

CREATE INDEX idx_recipient_preferences_recipient_id
    ON recipient_preferences(recipient_id);

CREATE INDEX idx_deliveries_notification_id
    ON deliveries(notification_id);

CREATE INDEX idx_deliveries_status
    ON deliveries(status);

CREATE INDEX idx_audit_events_notification_id
    ON audit_events(notification_id);