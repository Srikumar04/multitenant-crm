package com.srikumar.multitenantcrm.webhook;

import com.srikumar.multitenantcrm.testsupport.AbstractHttpIsolationTest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves webhook delivery actually happens over HTTP with a correct HMAC signature, using a tiny
 * local HTTP server as the stand-in receiver (no mocking framework needed).
 */
class WebhookDeliveryTest extends AbstractHttpIsolationTest {

    private HttpServer receiver;

    @AfterEach
    void stopReceiver() {
        if (receiver != null) {
            receiver.stop(0);
        }
    }

    @Test
    void contactCreationDeliversASignedWebhook() throws IOException, NoSuchAlgorithmException, InterruptedException {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedSignature = new AtomicReference<>();
        receiver = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        receiver.createContext("/hook", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedBody.set(new String(body, StandardCharsets.UTF_8));
            receivedSignature.set(exchange.getRequestHeaders().getFirst("X-Webhook-Signature"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        receiver.start();
        int port = receiver.getAddress().getPort();

        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String token = login(tenantId, "admin@acme.test", "pw-acme");

        ResponseEntity<Map> subResponse = restTemplate.exchange(
                url("/api/webhooks"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("url", "http://localhost:" + port + "/hook", "eventTypes", java.util.List.of("contact.created")),
                        bearer(token)),
                Map.class);
        assertThat(subResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String secret = (String) subResponse.getBody().get("secret");

        restTemplate.exchange(
                url("/api/contacts"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Alice", "email", "alice@acme.test"), bearer(token)),
                Map.class);

        waitUntil(() -> receivedBody.get() != null, Duration.ofSeconds(5));

        assertThat(receivedBody.get()).contains("\"eventType\":\"contact.created\"");
        assertThat(receivedBody.get()).doesNotContain("\"createdAt\":null");
        String expectedSignature = "sha256=" + hmacHex(secret, receivedBody.get());
        assertThat(receivedSignature.get()).isEqualTo(expectedSignature);
    }

    private static String hmacHex(String secret, String payload) throws NoSuchAlgorithmException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, Duration timeout)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Condition not met within " + timeout);
            }
            Thread.sleep(50);
        }
    }
}
