package com.srikumar.multitenantcrm.ticket;

import com.srikumar.multitenantcrm.contact.ContactResponse;
import com.srikumar.multitenantcrm.testsupport.AbstractHttpIsolationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class TicketCrudTest extends AbstractHttpIsolationTest {

    @Test
    void createTicketForOwnTenantsContactSucceeds() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String token = login(tenantId, "admin@acme.test", "pw-acme");
        UUID contactId = createContact(token, "Alice", "alice@acme.test");

        ResponseEntity<TicketResponse> response = restTemplate.exchange(
                url("/api/tickets"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("contactId", contactId.toString(), "title", "Broken login", "status", "OPEN", "priority", "HIGH"),
                        bearer(token)),
                TicketResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().title()).isEqualTo("Broken login");
        assertThat(response.getBody().contactId()).isEqualTo(contactId);
    }

    @Test
    void creatingATicketForAnotherTenantsContactIsRejected() {
        UUID tenantA = onboardTenant("Tenant A", "admin-a@example.com", "pw-a");
        UUID tenantB = onboardTenant("Tenant B", "admin-b@example.com", "pw-b");
        String tokenA = login(tenantA, "admin-a@example.com", "pw-a");
        String tokenB = login(tenantB, "admin-b@example.com", "pw-b");
        UUID contactInB = createContact(tokenB, "Bob", "bob@b.test");

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/tickets"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("contactId", contactInB.toString(), "title", "Bad ref", "status", "OPEN", "priority", "LOW"),
                        bearer(tokenA)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listTicketsReturnsOnlyOwnTenantsTickets() {
        UUID tenantA = onboardTenant("Tenant A", "admin-a@example.com", "pw-a");
        UUID tenantB = onboardTenant("Tenant B", "admin-b@example.com", "pw-b");
        String tokenA = login(tenantA, "admin-a@example.com", "pw-a");
        String tokenB = login(tenantB, "admin-b@example.com", "pw-b");
        createTicket(tokenA, createContact(tokenA, "Alice", "alice@a.test"), "A's ticket");
        createTicket(tokenB, createContact(tokenB, "Bob", "bob@b.test"), "B's ticket");

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/tickets"), HttpMethod.GET, new HttpEntity<>(bearer(tokenA)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var content = (Iterable<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.iterator().next().get("title")).isEqualTo("A's ticket");
    }

    private UUID createContact(String token, String name, String email) {
        ResponseEntity<ContactResponse> response = restTemplate.exchange(
                url("/api/contacts"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name, "email", email), bearer(token)),
                ContactResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private UUID createTicket(String token, UUID contactId, String title) {
        ResponseEntity<TicketResponse> response = restTemplate.exchange(
                url("/api/tickets"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("contactId", contactId.toString(), "title", title, "status", "OPEN", "priority", "LOW"),
                        bearer(token)),
                TicketResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }
}
