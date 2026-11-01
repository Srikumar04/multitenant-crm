package com.srikumar.multitenantcrm.webhook;

import java.util.List;
import java.util.UUID;

/** secret is included only on creation (callers must store it then - it's never returned again). */
public record WebhookSubscriptionResponse(UUID id, String url, List<String> eventTypes, String secret) {

    public static WebhookSubscriptionResponse from(WebhookSubscription subscription) {
        return new WebhookSubscriptionResponse(
                subscription.getId(), subscription.getUrl(), subscription.getEventTypesList(), subscription.getSecret());
    }
}
