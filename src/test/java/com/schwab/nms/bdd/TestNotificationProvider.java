package com.schwab.nms.bdd;

import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.enums.Channel;
import com.schwab.nms.database.entities.enums.FailureType;
import com.schwab.nms.modules.delivery.model.DeliveryResult;
import com.schwab.nms.modules.provider.NotificationProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestNotificationProvider
        implements NotificationProvider {

    private boolean failTransiently;
    private boolean failPermanently;

    @Override
    public Channel getChannel() {
        return Channel.EMAIL;
    }

    @Override
    public DeliveryResult send(Delivery delivery) {

        if (failTransiently) {
            return new DeliveryResult(
                    false,
                    FailureType.TRANSIENT,
                    "Simulated transient provider failure",
                    null
            );
        }

        if (failPermanently) {
            return new DeliveryResult(
                    false,
                    FailureType.PERMANENT,
                    "Simulated permanent provider failure",
                    null
            );
        }

        return new DeliveryResult(
                true,
                null,
                null,
                "TEST-" + delivery.getId()
        );
    }

    public void setFailTransiently(boolean failTransiently) {
        this.failTransiently = failTransiently;
    }

    public void setFailPermanently(boolean failPermanently) {
        this.failPermanently = failPermanently;
    }
}