package com.srikumar.multitenantcrm.webhook;

import com.srikumar.multitenantcrm.tenancy.TenantContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs on a background executor thread, never the request thread (see docs/decisions.md ADR
 * 0007). Must not read TenantContext - it isn't populated here; the caller passes everything this
 * needs as plain parameters, and TenantContext is set locally only around the dead-letter write.
 */
@Service
public class WebhookDeliveryService {

    private final WebhookDeadLetterRepository webhookDeadLetterRepository;
    private final TransactionTemplate transactionTemplate;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final int maxAttempts;
    private final List<Long> backoffMillis;

    public WebhookDeliveryService(
            WebhookDeadLetterRepository webhookDeadLetterRepository,
            TransactionTemplate transactionTemplate,
            @Value("${app.webhooks.max-attempts}") int maxAttempts,
            @Value("${app.webhooks.backoff-ms}") String backoffMillisCsv) {
        this.webhookDeadLetterRepository = webhookDeadLetterRepository;
        this.transactionTemplate = transactionTemplate;
        this.maxAttempts = maxAttempts;
        this.backoffMillis = List.of(backoffMillisCsv.split(",")).stream().map(Long::parseLong).toList();
    }

    @Async("webhookExecutor")
    public void deliverAsync(
            UUID tenantId, UUID subscriptionId, String url, String secret, String eventType, String payloadJson) {
        String lastError = null;
        int attempt = 0;
        while (attempt < maxAttempts) {
            attempt++;
            try {
                send(url, secret, payloadJson);
                return;
            } catch (Exception e) {
                lastError = e.getMessage();
                if (attempt < maxAttempts) {
                    sleep(backoffMillis.get(Math.min(attempt - 1, backoffMillis.size() - 1)));
                }
            }
        }
        recordDeadLetter(tenantId, subscriptionId, eventType, payloadJson, lastError, attempt);
    }

    private void send(String url, String secret, String payloadJson) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("X-Webhook-Signature", "sha256=" + sign(secret, payloadJson))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Webhook receiver returned HTTP " + response.statusCode());
        }
    }

    private String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to sign webhook payload", e);
        }
    }

    private void recordDeadLetter(
            UUID tenantId, UUID subscriptionId, String eventType, String payloadJson, String lastError, int attempts) {
        TenantContext.setTenantId(tenantId);
        try {
            transactionTemplate.execute(status -> {
                WebhookDeadLetter deadLetter = new WebhookDeadLetter();
                deadLetter.setId(UUID.randomUUID());
                deadLetter.setTenantId(tenantId);
                deadLetter.setSubscriptionId(subscriptionId);
                deadLetter.setEventType(eventType);
                deadLetter.setPayload(payloadJson);
                deadLetter.setLastError(lastError);
                deadLetter.setAttempts(attempts);
                return webhookDeadLetterRepository.save(deadLetter);
            });
        } finally {
            TenantContext.clear();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
