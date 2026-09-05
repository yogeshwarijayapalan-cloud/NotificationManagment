package com.schwab.nms.database.entities;

import com.schwab.nms.database.entities.enums.Channel;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
        name = "recipient_preferences",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_recipient_channel",
                        columnNames = {"recipient_id", "channel"}
                )
        }
)
public class RecipientPreference {

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Recipient recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    @Column(nullable = false)
    private boolean enabled = true;

    public RecipientPreference() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Recipient getRecipient() {
        return recipient;
    }

    public void setRecipient(Recipient recipient) {
        this.recipient = recipient;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}