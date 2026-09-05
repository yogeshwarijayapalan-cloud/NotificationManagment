package com.schwab.nms.modules.provider;

import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.enums.Channel;
import com.schwab.nms.modules.delivery.model.DeliveryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class EmailNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationProvider.class);

    @Override
    public Channel getChannel() {
        return Channel.EMAIL;
    }

    @Override
    public DeliveryResult send(Delivery delivery) {
        String providerMessageId = "EMAIL-" + delivery.getId();

        log.info("Email delivery sent: deliveryId={}, providerMessageId={}",
                delivery.getId(), providerMessageId);

        return new DeliveryResult(true, null, null, providerMessageId);
    }
}