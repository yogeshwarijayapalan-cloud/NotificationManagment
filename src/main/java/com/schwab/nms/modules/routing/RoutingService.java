package com.schwab.nms.modules.routing;

import com.schwab.nms.database.entities.Notification;
import com.schwab.nms.database.entities.Recipient;
import com.schwab.nms.database.entities.enums.Channel;

import java.util.List;

public interface RoutingService {

    List<Channel> determineChannels(
            Notification notification,
            Recipient recipient,
            List<Channel> requestedChannels);
}