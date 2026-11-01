package com.srikumar.multitenantcrm.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Called from within a CRUD service's existing TransactionTemplate block. Defers the actual
 * subscriber lookup + dispatch to run only after that transaction commits (ADR 0007) - never
 * for a change that ends up rolled back, and never a webhook fired for a change still ambiguous
 * for the caller.
 */
@Service
public class WebhookDispatchService {

    private final WebhookSubscriptionRepository webhookSubscriptionRepository;
    private final WebhookDeliveryService webhookDeliveryService;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final ObjectMapper objectMapper;

    public WebhookDispatchService(
            WebhookSubscriptionRepository webhookSubscriptionRepository,
            WebhookDeliveryService webhookDeliveryService,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper) {
        this.webhookSubscriptionRepository = webhookSubscriptionRepository;
        this.webhookDeliveryService = webhookDeliveryService;
        this.objectMapper = objectMapper;
        // PROPAGATION_REQUIRES_NEW: a TransactionSynchronization#afterCommit callback runs while
        // the triggering transaction's resources are still bound to this thread (full cleanup
        // happens slightly later) - reusing the default REQUIRED TransactionTemplate here just
        // joins that already-committed, about-to-be-discarded transaction instead of starting a
        // fresh one, and the query silently sees nothing.
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void dispatchAfterCommit(UUID tenantId, String eventType, Object data) {
        String payloadJson = toJson(new WebhookEventPayload(eventType, tenantId, data));
        Runnable dispatch = () -> dispatchNow(tenantId, eventType, payloadJson);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    private void dispatchNow(UUID tenantId, String eventType, String payloadJson) {
        List<WebhookSubscription> subscriptions =
                requiresNewTransactionTemplate.execute(status -> webhookSubscriptionRepository.findAll());
        for (WebhookSubscription subscription : subscriptions) {
            if (subscription.getEventTypesList().contains(eventType)) {
                webhookDeliveryService.deliverAsync(
                        tenantId, subscription.getId(), subscription.getUrl(), subscription.getSecret(), eventType, payloadJson);
            }
        }
    }

    private String toJson(WebhookEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }
    }
}
