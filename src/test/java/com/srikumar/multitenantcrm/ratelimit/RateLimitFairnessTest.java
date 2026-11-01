package com.srikumar.multitenantcrm.ratelimit;

import com.srikumar.multitenantcrm.testsupport.AbstractHttpIsolationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PROJECT_BRIEF.md section 7 Phase 4 done-when: one tenant being throttled must not affect
 * another. Overrides the free-plan limit to a tiny number so the test can exhaust it in a few
 * requests instead of sixty.
 */
class RateLimitFairnessTest extends AbstractHttpIsolationTest {

    @DynamicPropertySource
    static void tinyRateLimit(DynamicPropertyRegistry registry) {
        registry.add("app.rate-limit.free-requests-per-minute", () -> "3");
    }

    @Test
    void tenantHittingItsLimitDoesNotAffectAnotherTenant() {
        UUID tenantA = onboardTenant("Tenant A", "admin-a@example.com", "pw-a");
        UUID tenantB = onboardTenant("Tenant B", "admin-b@example.com", "pw-b");
        String tokenA = login(tenantA, "admin-a@example.com", "pw-a");
        String tokenB = login(tenantB, "admin-b@example.com", "pw-b");

        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/api/contacts"), HttpMethod.GET, new HttpEntity<>(bearer(tokenA)), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<Map> throttled = restTemplate.exchange(
                url("/api/contacts"), HttpMethod.GET, new HttpEntity<>(bearer(tokenA)), Map.class);
        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(throttled.getHeaders().getFirst("Retry-After")).isNotNull();

        ResponseEntity<String> responseForB = restTemplate.exchange(
                url("/api/contacts"), HttpMethod.GET, new HttpEntity<>(bearer(tokenB)), String.class);
        assertThat(responseForB.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
