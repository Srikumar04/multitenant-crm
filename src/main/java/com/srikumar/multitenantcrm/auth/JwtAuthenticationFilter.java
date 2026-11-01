package com.srikumar.multitenantcrm.auth;

import com.srikumar.multitenantcrm.tenancy.CurrentUserContext;
import com.srikumar.multitenantcrm.tenancy.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates SecurityContext (for @PreAuthorize role checks), TenantContext (for RLS), and MDC
 * (so every log line for this request carries `tenantId`, including the JSON logs the
 * "json-logs" profile emits - see docs/decisions.md ADR 0009) from the JWT's tenant_id/role
 * claims, before any controller or transaction runs. A missing/invalid token leaves no
 * Authentication set, which SecurityConfig's `.anyRequest().authenticated()` turns into a 401
 * for protected endpoints - the "reject requests without a valid tenant" requirement from
 * PROJECT_BRIEF.md section 7 Phase 2.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Request attribute mirroring the resolved tenant id, read by
     * {@link com.srikumar.multitenantcrm.config.MetricsConfig}'s observation convention instead
     * of {@link TenantContext}: the `http.server.requests` observation (registered by Spring's
     * ServerHttpObservationFilter, which wraps the whole Spring Security filter chain) only
     * computes its tags in a `finally` block that runs *after* this filter's own `finally` has
     * already cleared the TenantContext ThreadLocal. A plain request attribute survives that,
     * since it lives as long as the request object rather than the thread.
     */
    public static final String TENANT_ID_REQUEST_ATTRIBUTE = "tenantId";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                authenticate(header.substring(7), request);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            CurrentUserContext.clear();
            MDC.remove("tenantId");
        }
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            Claims claims = jwtService.parse(token).getPayload();
            UUID tenantId = UUID.fromString(claims.get("tenant_id", String.class));
            UUID userId = UUID.fromString(claims.get("user_id", String.class));
            String role = claims.get("role", String.class);

            TenantContext.setTenantId(tenantId);
            CurrentUserContext.setUserId(userId);
            MDC.put("tenantId", tenantId.toString());
            request.setAttribute(TENANT_ID_REQUEST_ATTRIBUTE, tenantId.toString());
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            var authentication =
                    new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
            CurrentUserContext.clear();
            MDC.remove("tenantId");
        }
    }
}
