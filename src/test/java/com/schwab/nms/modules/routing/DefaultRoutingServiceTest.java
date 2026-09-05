package com.schwab.nms.modules.routing;

import com.schwab.nms.database.entities.Notification;
import com.schwab.nms.database.entities.Recipient;
import com.schwab.nms.database.entities.RecipientPreference;
import com.schwab.nms.database.entities.enums.Channel;
import com.schwab.nms.database.entities.enums.Severity;
import com.schwab.nms.database.repository.RecipientPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DefaultRoutingServiceTest {

    private RecipientPreferenceRepository preferenceRepository;
    private DefaultRoutingService routingService;

    @BeforeEach
    void setUp() {
        preferenceRepository = mock(RecipientPreferenceRepository.class);
        routingService = new DefaultRoutingService(preferenceRepository);
    }

    @Test
    void shouldRouteToRequestedChannelWhenNoPreferencesExist() {
        Recipient recipient = recipientWithEmail();
        Notification notification = notificationWithSeverity(Severity.LOW);

        when(preferenceRepository.findByRecipientId(recipient.getId()))
                .thenReturn(List.of());

        List<Channel> result =
                routingService.determineChannels(
                        notification,
                        recipient,
                        List.of(Channel.EMAIL)
                );

        assertEquals(List.of(Channel.EMAIL), result);
    }

    @Test
    void shouldRespectRecipientPreferences() {
        Recipient recipient =
                recipientWithEmail();

        Notification notification =
                notificationWithSeverity(Severity.LOW);

        RecipientPreference preference = new RecipientPreference();
        preference.setChannel(Channel.EMAIL);
        preference.setEnabled(true);

        when(preferenceRepository.findByRecipientId(recipient.getId()))
                .thenReturn(List.of(preference));

        List<Channel> result =
                routingService.determineChannels(
                        notification,
                        recipient,
                        List.of(Channel.EMAIL, Channel.SMS)
                );

        assertEquals(List.of(Channel.EMAIL), result);
    }

    @Test
    void shouldNotRouteToUnsupportedChannel() {
        Recipient recipient = recipientWithEmail();
        Notification notification = notificationWithSeverity(Severity.LOW);

        when(preferenceRepository.findByRecipientId(recipient.getId()))
                .thenReturn(List.of());

        List<Channel> result =
                routingService.determineChannels(
                        notification,
                        recipient,
                        List.of(Channel.SMS)
                );

        assertEquals(List.of(), result);
    }

    @Test
    void shouldAddSmsForHighSeverity() {
        Recipient recipient =
                recipient("user@example.com", "+12145551234");

        Notification notification =
                notificationWithSeverity(Severity.HIGH);

        when(preferenceRepository.findByRecipientId(recipient.getId()))
                .thenReturn(List.of());

        List<Channel> result =
                routingService.determineChannels(
                        notification,
                        recipient,
                        List.of(Channel.EMAIL)
                );

        assertEquals(
                List.of(Channel.EMAIL, Channel.SMS),
                result
        );
    }

    @Test
    void shouldAddSmsAndPushForCriticalSeverity() {
        Recipient recipient =
                recipient("user@example.com", "+12145551234");

        Notification notification =
                notificationWithSeverity(Severity.CRITICAL);

        when(preferenceRepository.findByRecipientId(recipient.getId()))
                .thenReturn(List.of());

        List<Channel> result =
                routingService.determineChannels(
                        notification,
                        recipient,
                        List.of(Channel.EMAIL)
                );

        assertEquals(
                List.of(Channel.EMAIL, Channel.SMS, Channel.PUSH),
                result
        );
    }

    @Test
    void shouldNotAddMandatoryChannelWhenRecipientDisabledIt() {
        Recipient recipient =
                recipient("user@example.com", "+12145551234");

        Notification notification =
                notificationWithSeverity(Severity.HIGH);

        RecipientPreference emailPreference = new RecipientPreference();
        emailPreference.setChannel(Channel.EMAIL);
        emailPreference.setEnabled(true);

        RecipientPreference smsPreference = new RecipientPreference();
        smsPreference.setChannel(Channel.SMS);
        smsPreference.setEnabled(false);

        when(preferenceRepository.findByRecipientId(recipient.getId()))
                .thenReturn(List.of(
                        emailPreference,
                        smsPreference
                ));

        List<Channel> result =
                routingService.determineChannels(
                        notification,
                        recipient,
                        List.of(Channel.EMAIL)
                );

        assertEquals(
                List.of(Channel.EMAIL),
                result
        );
    }

    private Recipient recipientWithEmail() {
        return recipient("user@example.com", null);
    }

    private Recipient recipient(String email, String phone) {
        Recipient recipient = new Recipient();
        recipient.setId(UUID.randomUUID());
        recipient.setEmail(email);
        recipient.setPhone(phone);
        return recipient;
    }

    private Notification notificationWithSeverity(Severity severity) {
        Notification notification = new Notification();
        notification.setSeverity(severity);
        return notification;
    }
}