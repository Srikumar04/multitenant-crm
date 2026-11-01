package com.srikumar.multitenantcrm.webhook;

import com.srikumar.multitenantcrm.tenancy.TenantContext;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WebhookSubscriptionService {

    private final WebhookSubscriptionRepository webhookSubscriptionRepository;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebhookSubscriptionService(
            WebhookSubscriptionRepository webhookSubscriptionRepository, TransactionTemplate transactionTemplate) {
        this.webhookSubscriptionRepository = webhookSubscriptionRepository;
        this.transactionTemplate = transactionTemplate;
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
