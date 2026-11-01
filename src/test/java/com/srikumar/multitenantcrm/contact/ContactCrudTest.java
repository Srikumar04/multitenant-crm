package com.srikumar.multitenantcrm.contact;

import com.srikumar.multitenantcrm.testsupport.AbstractHttpIsolationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ContactCrudTest extends AbstractHttpIsolationTest {

    @Test
    void createThenGetReturnsTheSameContact() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String token = login(tenantId, "admin@acme.test", "pw-acme");

        ResponseEntity<ContactResponse> createResponse = restTemplate.exchange(
                url("/api/contacts"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Alice", "email", "alice@acme.test"), bearer(token)),
                ContactResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody().createdAt()).isNotNull();
        UUID contactId = createResponse.getBody().id();

        ResponseEntity<ContactResponse> getResponse = restTemplate.exchange(
                url("/api/contacts/" + contactId), HttpMethod.GET, new HttpEntity<>(bearer(token)), ContactResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().name()).isEqualTo("Alice");
    }

    @Test
    void createWithBlankNameReturnsProblemDetail() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String token = login(tenantId, "admin@acme.test", "pw-acme");

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/contacts"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "", "email", "x@acme.test"), bearer(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).contains("problem+json");
    }

    @Test
    void updateChangesTheContact() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String token = login(tenantId, "admin@acme.test", "pw-acme");
        UUID contactId = createContact(token, "Alice", "alice@acme.test");

        ResponseEntity<ContactResponse> updateResponse = restTemplate.exchange(
                url("/api/contacts/" + contactId),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Alice Updated", "email", "alice@acme.test"), bearer(token)),
                ContactResponse.class);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().name()).isEqualTo("Alice Updated");
    }

    @Test
    void deleteRemovesTheContact() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String token = login(tenantId, "admin@acme.test", "pw-acme");
        UUID contactId = createContact(token, "Alice", "alice@acme.test");

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                url("/api/contacts/" + contactId), HttpMethod.DELETE, new HttpEntity<>(bearer(token)), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getResponse = restTemplate.exchange(
                url("/api/contacts/" + contactId), HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anotherTenantsContactLooksLikeItDoesNotExist() {
        UUID tenantA = onboardTenant("Tenant A", "admin-a@example.com", "pw-a");
        UUID tenantB = onboardTenant("Tenant B", "admin-b@example.com", "pw-b");
        String tokenA = login(tenantA, "admin-a@example.com", "pw-a");
        String tokenB = login(tenantB, "admin-b@example.com", "pw-b");
        UUID contactInA = createContact(tokenA, "Alice", "alice@a.test");

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/contacts/" + contactInA), HttpMethod.GET, new HttpEntity<>(bearer(tokenB)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listIsPaginated() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String token = login(tenantId, "admin@acme.test", "pw-acme");
        for (int i = 0; i < 3; i++) {
            createContact(token, "Contact " + i, "contact" + i + "@acme.test");
        }

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/contacts?page=0&size=2"), HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> page = (Map<String, Object>) response.getBody().get("page");
        assertThat((Iterable<?>) response.getBody().get("content")).hasSize(2);
        assertThat(page.get("totalElements")).isEqualTo(3);
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
}
