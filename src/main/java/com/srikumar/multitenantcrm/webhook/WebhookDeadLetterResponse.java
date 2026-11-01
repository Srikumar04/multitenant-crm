package com.srikumar.multitenantcrm.webhook;

import java.time.Instant;
import java.util.UUID;

public record WebhookDeadLetterResponse(
        UUID id, UUID subscriptionId, String eventType, String payload, String lastError, int attempts, Instant createdAt) {

    public static WebhookDeadLetterResponse from(WebhookDeadLetter deadLetter) {
        return new WebhookDeadLetterResponse(
                deadLetter.getId(),
                deadLetter.getSubscriptionId(),
                deadLetter.getEventType(),
                deadLetter.getPayload(),
                deadLetter.getLastError(),
                deadLetter.getAttempts(),
                deadLetter.getCreatedAt());
    }
}
