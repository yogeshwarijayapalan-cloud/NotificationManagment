package com.schwab.nms.modules.provider;

import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.enums.Channel;
import com.schwab.nms.modules.delivery.model.DeliveryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PushNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationProvider.class);

    @Override
    public Channel getChannel() {
        return Channel.PUSH;
    }

    @Override
    public DeliveryResult send(Delivery delivery) {
        String providerMessageId = "PUSH-" + delivery.getId();

        log.info("Push notification sent: deliveryId={}, providerMessageId={}",
                delivery.getId(), providerMessageId);

        return new DeliveryResult(true, null, null, providerMessageId);
    }
}