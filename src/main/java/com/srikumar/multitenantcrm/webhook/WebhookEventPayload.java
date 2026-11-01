package com.srikumar.multitenantcrm.webhook;

import java.util.UUID;

public record WebhookEventPayload(String eventType, UUID tenantId, Object data) {
}
