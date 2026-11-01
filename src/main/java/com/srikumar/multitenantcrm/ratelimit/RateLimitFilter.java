package com.srikumar.multitenantcrm.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srikumar.multitenantcrm.config.MetricsConfig;
import com.srikumar.multitenantcrm.tenancy.TenantContext;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs after JwtAuthenticationFilter (TenantContext is already populated). Unauthenticated
 * requests (no tenant resolved) are not rate-limited here - they're rejected by Spring Security
 * before reaching a protected endpoint anyway.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public RateLimitFilter(RateLimitService rateLimitService, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        ConsumptionProbe probe = rateLimitService.tryConsume(tenantId);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        meterRegistry.counter(MetricsConfig.RATE_LIMIT_REJECTIONS_METRIC, "tenant_id", tenantId.toString())
                .increment();

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"));
    }
}
