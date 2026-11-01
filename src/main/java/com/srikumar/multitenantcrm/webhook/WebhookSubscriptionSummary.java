package com.srikumar.multitenantcrm.webhook;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Unlike {@link WebhookSubscriptionResponse}, never carries the secret - that's shown once, at creation. */
public record WebhookSubscriptionSummary(UUID id, String url, List<String> eventTypes, Instant createdAt) {

    public static WebhookSubscriptionSummary from(WebhookSubscription subscription) {
        return new WebhookSubscriptionSummary(
                subscription.getId(), subscription.getUrl(), subscription.getEventTypesList(), subscription.getCreatedAt());
    }
}
