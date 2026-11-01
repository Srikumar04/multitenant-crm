package com.srikumar.multitenantcrm.testsupport;

import com.srikumar.multitenantcrm.auth.LoginResponse;
import com.srikumar.multitenantcrm.tenant.TenantOnboardingResponse;
import com.srikumar.multitenantcrm.user.User;
import com.srikumar.multitenantcrm.user.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Like {@link AbstractIsolationTest} but with a real HTTP server, for tests that exercise the
 * JWT filter + tenant resolution end to end rather than driving repositories directly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractHttpIsolationTest extends AbstractIsolationTest {

    protected static final String PLATFORM_ADMIN_USERNAME = "platform-admin";
    protected static final String PLATFORM_ADMIN_PASSWORD = "change-me-dev-only";

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected UUID onboardTenant(String name, String adminEmail, String adminPassword) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(PLATFORM_ADMIN_USERNAME, PLATFORM_ADMIN_PASSWORD);

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

    /** No onboarding endpoint creates non-ADMIN users (out of Phase 3 scope), so seed directly. */
    protected void createUser(UUID tenantId, String email, String rawPassword, String role) {
        asTenant(tenantId, () -> {
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setTenantId(tenantId);
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setRole(role);
            return userRepository.save(user);
        });
    }

    protected String login(UUID tenantId, String email, String password) {
        Map<String, Object> request = Map.of(
                "tenantId", tenantId.toString(),
                "email", email,
                "password", password);

        ResponseEntity<LoginResponse> response =
                restTemplate.postForEntity(url("/api/auth/login"), request, LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().token();
    }

    protected HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
