package com.srikumar.multitenantcrm.webhook;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookSubscriptionController {

    private final WebhookSubscriptionService webhookSubscriptionService;

    public WebhookSubscriptionController(WebhookSubscriptionService webhookSubscriptionService) {
        this.webhookSubscriptionService = webhookSubscriptionService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebhookSubscriptionResponse> create(@Valid @RequestBody WebhookSubscriptionRequest request) {
        WebhookSubscriptionResponse response = webhookSubscriptionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
