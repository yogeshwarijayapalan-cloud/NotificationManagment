package com.schwab.nms.modules.provider;

import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.enums.Channel;
import com.schwab.nms.modules.delivery.model.DeliveryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationProvider.class);

    @Override
    public Channel getChannel() {
        return Channel.SMS;
    }

    @Override
    public DeliveryResult send(Delivery delivery) {
        String providerMessageId = "SMS-" + delivery.getId();

        log.info("SMS notification sent: deliveryId={}, providerMessageId={}",
                delivery.getId(), providerMessageId);

        return new DeliveryResult(true, null, null, providerMessageId);
    }
}