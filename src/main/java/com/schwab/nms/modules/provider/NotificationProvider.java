package com.schwab.nms.modules.provider;

import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.enums.Channel;
import com.schwab.nms.modules.delivery.model.DeliveryResult;

public interface NotificationProvider {

    Channel getChannel();

    DeliveryResult send(Delivery delivery);
}

