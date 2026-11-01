package com.srikumar.multitenantcrm.audit;

import com.srikumar.multitenantcrm.testsupport.AbstractHttpIsolationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** PROJECT_BRIEF.md section 6: viewing the audit log is ADMIN-only. */
class AuditLogAccessTest extends AbstractHttpIsolationTest {

    @Test
    void adminCanListEntriesButAgentAndViewerCannot() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String adminToken = login(tenantId, "admin@acme.test", "pw-acme");

        restTemplate.exchange(
                url("/api/contacts"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Alice", "email", "alice@acme.test"), bearer(adminToken)),
                Map.class);

        ResponseEntity<Map> adminResponse = restTemplate.exchange(
                url("/api/audit-log"), HttpMethod.GET, new HttpEntity<>(bearer(adminToken)), Map.class);
        assertThat(adminResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) adminResponse.getBody().toString()).contains("CREATE");

        createUser(tenantId, "agent@acme.test", "pw-agent", "AGENT");
        String agentToken = login(tenantId, "agent@acme.test", "pw-agent");
        ResponseEntity<Map> agentResponse = restTemplate.exchange(
                url("/api/audit-log"), HttpMethod.GET, new HttpEntity<>(bearer(agentToken)), Map.class);
        assertThat(agentResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        createUser(tenantId, "viewer@acme.test", "pw-viewer", "VIEWER");
        String viewerToken = login(tenantId, "viewer@acme.test", "pw-viewer");
        ResponseEntity<Map> viewerResponse = restTemplate.exchange(
                url("/api/audit-log"), HttpMethod.GET, new HttpEntity<>(bearer(viewerToken)), Map.class);
        assertThat(viewerResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
