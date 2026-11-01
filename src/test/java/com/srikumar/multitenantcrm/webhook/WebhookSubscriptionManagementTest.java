package com.srikumar.multitenantcrm.webhook;

import com.srikumar.multitenantcrm.testsupport.AbstractHttpIsolationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSubscriptionManagementTest extends AbstractHttpIsolationTest {

    @Test
    void adminCanListAndDeleteSubscriptionsWithoutTheListLeakingTheSecret() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String adminToken = login(tenantId, "admin@acme.test", "pw-acme");

        ResponseEntity<WebhookSubscriptionResponse> created = restTemplate.exchange(
                url("/api/webhooks"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("url", "http://example.test/hook", "eventTypes", List.of("contact.created")),
                        bearer(adminToken)),
                WebhookSubscriptionResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID subscriptionId = created.getBody().id();
        assertThat(created.getBody().secret()).isNotBlank();

        ResponseEntity<WebhookSubscriptionSummary[]> listResponse = restTemplate.exchange(
                url("/api/webhooks"),
                HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken)),
                WebhookSubscriptionSummary[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(1);
        assertThat(listResponse.getBody()[0].id()).isEqualTo(subscriptionId);
        // WebhookSubscriptionSummary has no secret field at all - compile-time guarantee the
        // list response can't leak it, on top of this runtime check of the raw JSON.
        ResponseEntity<String> rawListResponse =
                restTemplate.exchange(url("/api/webhooks"), HttpMethod.GET, new HttpEntity<>(bearer(adminToken)), String.class);
        assertThat(rawListResponse.getBody()).doesNotContain("secret");

        restTemplate.exchange(
                url("/api/webhooks/" + subscriptionId), HttpMethod.DELETE, new HttpEntity<>(bearer(adminToken)), Void.class);

        ResponseEntity<WebhookSubscriptionSummary[]> afterDelete = restTemplate.exchange(
                url("/api/webhooks"),
                HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken)),
                WebhookSubscriptionSummary[].class);
        assertThat(afterDelete.getBody()).isEmpty();
    }

    @Test
    void agentCannotListCreateOrDeleteSubscriptions() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        createUser(tenantId, "agent@acme.test", "pw-agent", "AGENT");
        String agentToken = login(tenantId, "agent@acme.test", "pw-agent");

        ResponseEntity<Map> listResponse = restTemplate.exchange(
                url("/api/webhooks"), HttpMethod.GET, new HttpEntity<>(bearer(agentToken)), Map.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> deadLettersResponse = restTemplate.exchange(
                url("/api/webhooks/dead-letters"), HttpMethod.GET, new HttpEntity<>(bearer(agentToken)), Map.class);
        assertThat(deadLettersResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanReadDeadLettersEvenWhenEmpty() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String adminToken = login(tenantId, "admin@acme.test", "pw-acme");

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/webhooks/dead-letters"), HttpMethod.GET, new HttpEntity<>(bearer(adminToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
