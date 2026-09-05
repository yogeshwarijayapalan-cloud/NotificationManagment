package com.schwab.nms.modules.routing;

import com.schwab.nms.database.entities.Notification;
import com.schwab.nms.database.entities.Recipient;
import com.schwab.nms.database.entities.RecipientPreference;
import com.schwab.nms.database.entities.enums.Channel;
import com.schwab.nms.database.entities.enums.Severity;
import com.schwab.nms.database.repository.RecipientPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class DefaultRoutingService implements RoutingService {

    private static final Logger log = LoggerFactory.getLogger(DefaultRoutingService.class);

    private final RecipientPreferenceRepository preferenceRepository;

    public DefaultRoutingService(RecipientPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    @Override
    public List<Channel> determineChannels(
            Notification notification,
            Recipient recipient,
            List<Channel> requestedChannels) {

        Set<Channel> channels = EnumSet.noneOf(Channel.class);
        List<RecipientPreference> preferences =
                preferenceRepository.findByRecipientId(recipient.getId());

        Set<Channel> preferredChannels = EnumSet.noneOf(Channel.class);

        for (RecipientPreference preference : preferences) {
            if (preference.isEnabled()) {
                preferredChannels.add(preference.getChannel());
            }
        }

        for (Channel requested : requestedChannels) {
            if ((preferences.isEmpty() || preferredChannels.contains(requested))
                    && supportsChannel(recipient, requested)) {
                channels.add(requested);
            }
        }

        addMandatoryChannels(
                notification.getSeverity(),
                recipient,
                channels,
                preferredChannels,
                preferences);

        List<Channel> selectedChannels = new ArrayList<>(channels);

        log.debug("Channels selected: notificationId={}, recipientId={}, severity={}, channels={}",
                notification.getId(),
                recipient.getId(),
                notification.getSeverity(),
                selectedChannels);

        return selectedChannels;
    }

    private void addMandatoryChannels(
            Severity severity,
            Recipient recipient,
            Set<Channel> channels,
            Set<Channel> preferredChannels,
            List<RecipientPreference> preferences) {

        if (severity == Severity.HIGH || severity == Severity.CRITICAL) {
            addIfSupported(Channel.SMS, recipient, channels, preferredChannels, preferences);
        }

        if (severity == Severity.CRITICAL) {
            addIfSupported(Channel.PUSH, recipient, channels, preferredChannels, preferences);
        }
    }

    private void addIfSupported(
            Channel channel,
            Recipient recipient,
            Set<Channel> channels,
            Set<Channel> preferredChannels,
            List<RecipientPreference> preferences) {

        if ((preferences.isEmpty() || preferredChannels.contains(channel))
                && supportsChannel(recipient, channel)) {
            channels.add(channel);
        }
    }

    private boolean supportsChannel(Recipient recipient, Channel channel) {
        return switch (channel) {
            case EMAIL -> recipient.getEmail() != null && !recipient.getEmail().isBlank();
            case SMS -> recipient.getPhone() != null && !recipient.getPhone().isBlank();
            case PUSH -> true;
        };
    }
}