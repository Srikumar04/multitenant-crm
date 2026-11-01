package com.srikumar.multitenantcrm.webhook;

import com.srikumar.multitenantcrm.testsupport.AbstractHttpIsolationTest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves a receiver that always fails ends up as a dead-letter row after retries are exhausted. */
class WebhookDeadLetterTest extends AbstractHttpIsolationTest {

    @DynamicPropertySource
    static void fastRetries(DynamicPropertyRegistry registry) {
        registry.add("app.webhooks.max-attempts", () -> "2");
        registry.add("app.webhooks.backoff-ms", () -> "10");
    }

    @Autowired
    WebhookDeadLetterRepository webhookDeadLetterRepository;

    private HttpServer failingReceiver;

    @AfterEach
    void stopReceiver() {
        if (failingReceiver != null) {
            failingReceiver.stop(0);
        }
    }

    @Test
    void repeatedlyFailingReceiverEndsUpAsADeadLetter() throws IOException, InterruptedException {
        failingReceiver = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        failingReceiver.createContext("/hook", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        failingReceiver.start();
        int port = failingReceiver.getAddress().getPort();

        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String token = login(tenantId, "admin@acme.test", "pw-acme");

        ResponseEntity<Map> subResponse = restTemplate.exchange(
                url("/api/webhooks"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("url", "http://localhost:" + port + "/hook", "eventTypes", List.of("contact.created")),
                        bearer(token)),
                Map.class);
        assertThat(subResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        restTemplate.exchange(
                url("/api/contacts"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Alice", "email", "alice@acme.test"), bearer(token)),
                Map.class);

        List<WebhookDeadLetter> deadLetters = waitForDeadLetter(tenantId);

        assertThat(deadLetters).hasSize(1);
        assertThat(deadLetters.get(0).getEventType()).isEqualTo("contact.created");
        assertThat(deadLetters.get(0).getAttempts()).isEqualTo(2);
        assertThat(deadLetters.get(0).getLastError()).isNotNull();
    }

    private List<WebhookDeadLetter> waitForDeadLetter(UUID tenantId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            List<WebhookDeadLetter> found = asTenant(tenantId, () -> webhookDeadLetterRepository.findAll());
            if (!found.isEmpty()) {
                return found;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No dead letter appeared within the timeout");
    }
}
