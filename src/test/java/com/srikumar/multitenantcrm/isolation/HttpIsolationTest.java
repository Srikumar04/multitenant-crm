package com.srikumar.multitenantcrm.isolation;

import com.srikumar.multitenantcrm.auth.LoginResponse;
import com.srikumar.multitenantcrm.contact.Contact;
import com.srikumar.multitenantcrm.contact.ContactRepository;
import com.srikumar.multitenantcrm.tenant.TenantOnboardingResponse;
import com.srikumar.multitenantcrm.testsupport.AbstractHttpIsolationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves tenant isolation holds end to end through the HTTP API: logging in as two different
 * tenants' admins yields tokens that each resolve, via the JWT filter and TenantContext, to that
 * tenant's own data only - not to the other tenant's, and not to anyone without a valid token.
 */
class HttpIsolationTest extends AbstractHttpIsolationTest {

    @Autowired
    ContactRepository contactRepository;

    @Test
    void eachTenantsTokenOnlySeesItsOwnContactsOverHttp() {
        UUID tenantA = onboardTenant("Tenant A", "admin-a@example.com", "password-a");
        UUID tenantB = onboardTenant("Tenant B", "admin-b@example.com", "password-b");

        String tokenA = login(tenantA, "admin-a@example.com", "password-a");
        String tokenB = login(tenantB, "admin-b@example.com", "password-b");

        asTenant(tenantA, () -> contactRepository.save(contact(tenantA, "Alice")));
        asTenant(tenantB, () -> contactRepository.save(contact(tenantB, "Bob")));

        List<Map<String, Object>> contactsForA = getContacts(tokenA);
        List<Map<String, Object>> contactsForB = getContacts(tokenB);

        assertThat(contactsForA).hasSize(1);
        assertThat(contactsForA.get(0).get("name")).isEqualTo("Alice");
        assertThat(contactsForB).hasSize(1);
        assertThat(contactsForB.get(0).get("name")).isEqualTo("Bob");
    }

    @Test
    void requestWithoutTokenIsRejected() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(url("/api/contacts"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requestWithInvalidTokenIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("not-a-real-token");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/contacts"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getContacts(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<List> response = restTemplate.exchange(
                url("/api/contacts"), HttpMethod.GET, new HttpEntity<>(headers), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) response.getBody();
    }

    private UUID onboardTenant(String name, String adminEmail, String adminPassword) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("platform-admin", "change-me-dev-only");

        Map<String, Object> request = Map.of(
                "name", name,
                "plan", "free",
                "adminEmail", adminEmail,
                "adminPassword", adminPassword);

        ResponseEntity<TenantOnboardingResponse> response = restTemplate.exchange(
                url("/api/tenants"),
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                TenantOnboardingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().tenantId();
    }

    private String login(UUID tenantId, String email, String password) {
        Map<String, Object> request = Map.of(
                "tenantId", tenantId.toString(),
                "email", email,
                "password", password);

        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                url("/api/auth/login"), request, LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().token();
    }

    private Contact contact(UUID tenantId, String name) {
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setTenantId(tenantId);
        contact.setName(name);
        contact.setEmail(name.toLowerCase() + "@example.com");
        return contact;
    }
}
