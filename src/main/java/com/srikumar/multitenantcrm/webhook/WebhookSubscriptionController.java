package com.srikumar.multitenantcrm.webhook;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN-only throughout - webhook secrets and delivery failures are tenant-admin territory. */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookSubscriptionController {

    private final WebhookSubscriptionService webhookSubscriptionService;

    public WebhookSubscriptionController(WebhookSubscriptionService webhookSubscriptionService) {
        this.webhookSubscriptionService = webhookSubscriptionService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<WebhookSubscriptionSummary> list() {
        return webhookSubscriptionService.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebhookSubscriptionResponse> create(@Valid @RequestBody WebhookSubscriptionRequest request) {
        WebhookSubscriptionResponse response = webhookSubscriptionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        webhookSubscriptionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dead-letters")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedModel<WebhookDeadLetterResponse> deadLetters(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<WebhookDeadLetterResponse> page = webhookSubscriptionService.listDeadLetters(pageable);
        return new PagedModel<>(page);
    }
}
