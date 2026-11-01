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

/** PROJECT_BRIEF.md section 6: VIEWER is read-only; ADMIN/AGENT can write. */
class RoleBasedAccessTest extends AbstractHttpIsolationTest {

    @Test
    void viewerCanReadButNotWrite() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        createUser(tenantId, "viewer@acme.test", "pw-viewer", "VIEWER");
        String viewerToken = login(tenantId, "viewer@acme.test", "pw-viewer");

        ResponseEntity<Map> listResponse = restTemplate.exchange(
                url("/api/contacts"), HttpMethod.GET, new HttpEntity<>(bearer(viewerToken)), Map.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                url("/api/contacts"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Alice", "email", "alice@acme.test"), bearer(viewerToken)),
                Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
