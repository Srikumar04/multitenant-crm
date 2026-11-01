package com.srikumar.multitenantcrm.audit;

import com.srikumar.multitenantcrm.contact.ContactResponse;
import com.srikumar.multitenantcrm.testsupport.AbstractHttpIsolationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogWritesTest extends AbstractHttpIsolationTest {

    @Autowired
    AuditLogRepository auditLogRepository;

    @Test
    void createUpdateAndDeleteEachWriteAnAuditLogRow() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String token = login(tenantId, "admin@acme.test", "pw-acme");

        ResponseEntity<ContactResponse> createResponse = restTemplate.exchange(
                url("/api/contacts"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Alice", "email", "alice@acme.test"), bearer(token)),
                ContactResponse.class);
        UUID contactId = createResponse.getBody().id();

        restTemplate.exchange(
                url("/api/contacts/" + contactId),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Alice Updated", "email", "alice@acme.test"), bearer(token)),
                ContactResponse.class);

        restTemplate.exchange(
                url("/api/contacts/" + contactId), HttpMethod.DELETE, new HttpEntity<>(bearer(token)), Void.class);

        List<AuditLog> entries = asTenant(tenantId, () -> auditLogRepository.findAll());
        List<AuditLog> forContact = entries.stream().filter(e -> e.getEntityId().equals(contactId)).toList();

        assertThat(forContact).extracting(AuditLog::getAction).containsExactlyInAnyOrder("CREATE", "UPDATE", "DELETE");
        assertThat(forContact).allMatch(e -> e.getEntity().equals("Contact"));
        assertThat(forContact).allMatch(e -> e.getTenantId().equals(tenantId));
    }
}
