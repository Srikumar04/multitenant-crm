package com.srikumar.multitenantcrm.idempotency;

import com.srikumar.multitenantcrm.contact.ContactRepository;
import com.srikumar.multitenantcrm.testsupport.AbstractHttpIsolationTest;
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

/** PROJECT_BRIEF.md section 7 Phase 4: idempotency keys on create endpoints. */
class IdempotencyKeyTest extends AbstractHttpIsolationTest {

    @Autowired
    ContactRepository contactRepository;

    @Test
    void repeatingTheSameIdempotencyKeyReplaysTheFirstResponseAndCreatesOnlyOneContact() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String token = login(tenantId, "admin@acme.test", "pw-acme");
        String idempotencyKey = UUID.randomUUID().toString();

        HttpHeaders headers = bearer(token);
        headers.set("Idempotency-Key", idempotencyKey);
        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(Map.of("name", "Alice", "email", "alice@acme.test"), headers);

        ResponseEntity<Map> first =
                restTemplate.exchange(url("/api/contacts"), HttpMethod.POST, request, Map.class);
        ResponseEntity<Map> second =
                restTemplate.exchange(url("/api/contacts"), HttpMethod.POST, request, Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody()).isEqualTo(first.getBody());

        long contactCount = asTenant(tenantId, () -> contactRepository.count());
        assertThat(contactCount).isEqualTo(1);
    }
}
