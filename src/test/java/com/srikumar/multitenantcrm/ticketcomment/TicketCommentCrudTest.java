package com.srikumar.multitenantcrm.ticketcomment;

import com.srikumar.multitenantcrm.contact.ContactResponse;
import com.srikumar.multitenantcrm.testsupport.AbstractHttpIsolationTest;
import com.srikumar.multitenantcrm.ticket.TicketResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class TicketCommentCrudTest extends AbstractHttpIsolationTest {

    @Test
    void createThenListReturnsTheComment() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String token = login(tenantId, "admin@acme.test", "pw-acme");
        UUID ticketId = createTicket(token);

        ResponseEntity<TicketCommentResponse> createResponse = restTemplate.exchange(
                url("/api/tickets/" + ticketId + "/comments"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("body", "Looking into it"), bearer(token)),
                TicketCommentResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody().body()).isEqualTo("Looking into it");

        ResponseEntity<Map> listResponse = restTemplate.exchange(
                url("/api/tickets/" + ticketId + "/comments"), HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        var content = (Iterable<Map<String, Object>>) listResponse.getBody().get("content");
        assertThat(content).hasSize(1);
    }

    @Test
    void commentingOnAnotherTenantsTicketReturnsNotFound() {
        UUID tenantA = onboardTenant("Tenant A", "admin-a@example.com", "pw-a");
        UUID tenantB = onboardTenant("Tenant B", "admin-b@example.com", "pw-b");
        String tokenA = login(tenantA, "admin-a@example.com", "pw-a");
        String tokenB = login(tenantB, "admin-b@example.com", "pw-b");
        UUID ticketInA = createTicket(tokenA);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/tickets/" + ticketInA + "/comments"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("body", "Trying to comment"), bearer(tokenB)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private UUID createTicket(String token) {
        ResponseEntity<ContactResponse> contactResponse = restTemplate.exchange(
                url("/api/contacts"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Alice", "email", "alice@test.com"), bearer(token)),
                ContactResponse.class);
        UUID contactId = contactResponse.getBody().id();

        ResponseEntity<TicketResponse> ticketResponse = restTemplate.exchange(
                url("/api/tickets"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("contactId", contactId.toString(), "title", "Issue", "status", "OPEN", "priority", "LOW"),
                        bearer(token)),
                TicketResponse.class);
        return ticketResponse.getBody().id();
    }
}
