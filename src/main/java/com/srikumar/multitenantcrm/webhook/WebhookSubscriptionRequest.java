package com.srikumar.multitenantcrm.webhook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record WebhookSubscriptionRequest(@NotBlank String url, @NotEmpty List<String> eventTypes) {
}
