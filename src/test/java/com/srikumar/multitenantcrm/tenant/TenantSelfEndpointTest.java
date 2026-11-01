package com.srikumar.multitenantcrm.tenant;

import com.srikumar.multitenantcrm.testsupport.AbstractHttpIsolationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class TenantSelfEndpointTest extends AbstractHttpIsolationTest {

    @Test
    void returnsTheCallersOwnTenantAndRateLimitForItsPlan() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        String token = login(tenantId, "admin@acme.test", "pw-acme");

        ResponseEntity<TenantSelfResponse> response = restTemplate.exchange(
                url("/api/tenants/me"), HttpMethod.GET, new HttpEntity<>(bearer(token)), TenantSelfResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().tenantId()).isEqualTo(tenantId);
        assertThat(response.getBody().name()).isEqualTo("Acme");
        assertThat(response.getBody().plan()).isEqualTo("free");
        assertThat(response.getBody().rateLimitPerMinute()).isEqualTo(60);
    }

    @Test
    void aViewerCanAlsoReadIt() {
        UUID tenantId = onboardTenant("Acme", "admin@acme.test", "pw-acme");
        createUser(tenantId, "viewer@acme.test", "pw-viewer", "VIEWER");
        String viewerToken = login(tenantId, "viewer@acme.test", "pw-viewer");

        ResponseEntity<TenantSelfResponse> response = restTemplate.exchange(
                url("/api/tenants/me"),
                HttpMethod.GET,
                new HttpEntity<>(bearer(viewerToken)),
                TenantSelfResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
