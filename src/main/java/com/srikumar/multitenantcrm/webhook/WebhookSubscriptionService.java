package com.srikumar.multitenantcrm.webhook;

import com.srikumar.multitenantcrm.common.ResourceNotFoundException;
import com.srikumar.multitenantcrm.tenancy.TenantContext;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WebhookSubscriptionService {

    private final WebhookSubscriptionRepository webhookSubscriptionRepository;
    private final WebhookDeadLetterRepository webhookDeadLetterRepository;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebhookSubscriptionService(
            WebhookSubscriptionRepository webhookSubscriptionRepository,
            WebhookDeadLetterRepository webhookDeadLetterRepository,
            TransactionTemplate transactionTemplate) {
        this.webhookSubscriptionRepository = webhookSubscriptionRepository;
        this.webhookDeadLetterRepository = webhookDeadLetterRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public List<WebhookSubscriptionSummary> list() {
        return transactionTemplate
                .execute(status -> webhookSubscriptionRepository.findAll())
                .stream()
                .map(WebhookSubscriptionSummary::from)
                .toList();
    }

    public void delete(UUID id) {
        transactionTemplate.execute(status -> {
            WebhookSubscription existing = webhookSubscriptionRepository.findById(id).orElseThrow(() -> notFound(id));
            webhookSubscriptionRepository.delete(existing);
            return null;
        });
    }

    public Page<WebhookDeadLetterResponse> listDeadLetters(Pageable pageable) {
        return transactionTemplate
                .execute(status -> webhookDeadLetterRepository.findAll(pageable))
                .map(WebhookDeadLetterResponse::from);
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Webhook subscription " + id + " not found");
    }

    public WebhookSubscriptionResponse create(WebhookSubscriptionRequest request) {
        String secret = generateSecret();
        WebhookSubscription saved = transactionTemplate.execute(status -> {
            WebhookSubscription subscription = new WebhookSubscription();
            subscription.setId(UUID.randomUUID());
            subscription.setTenantId(TenantContext.getTenantId());
            subscription.setUrl(request.url());
            subscription.setEventTypes(String.join(",", request.eventTypes()));
            subscription.setSecret(secret);
            return webhookSubscriptionRepository.save(subscription);
        });
        return WebhookSubscriptionResponse.from(saved);
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
