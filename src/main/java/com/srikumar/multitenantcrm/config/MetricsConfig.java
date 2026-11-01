package com.srikumar.multitenantcrm.config;

import com.srikumar.multitenantcrm.auth.JwtAuthenticationFilter;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;

/**
 * Tags `http.server.requests` with `tenant_id` so per-tenant latency/error rate is queryable in
 * Prometheus (PROJECT_BRIEF.md section 7 Phase 5 item 1) - this is what lets the noisy-neighbor
 * benchmark (docs/benchmarks.md) show one tenant's flood against other tenants' p99 by tag,
 * rather than only in aggregate. Spring Boot 3's Micrometer Observation API replaced the old
 * (Boot 2) `WebMvcTagsProvider` hook with a {@link ServerRequestObservationConvention} bean, so
 * that's the extension point used here instead.
 *
 * <p>Tenants are created at runtime with no upper bound (tenant onboarding is a public-ish admin
 * API, not a fixed config list), so tagging every meter by raw tenant id is an unbounded-
 * cardinality time series in Prometheus - exactly the mistake "cardinality-safe" metrics design
 * warns against. {@link #tenantCardinalityCustomizer()} caps it: once more than
 * {@value #MAX_TRACKED_TENANTS} distinct tenants have been observed on a given meter, further
 * distinct tenant ids are denied (dropped) instead of growing the series forever. See
 * docs/decisions.md ADR 0010.
 */
@Configuration
public class MetricsConfig {

    private static final String TENANT_TAG = "tenant_id";
    private static final String NO_TENANT = "none";
    private static final int MAX_TRACKED_TENANTS = 300;

    /** Tagged by {@link #TENANT_TAG}; see {@link com.srikumar.multitenantcrm.ratelimit.RateLimitFilter}. */
    public static final String RATE_LIMIT_REJECTIONS_METRIC = "ratelimit.rejections";

    @Bean
    public ServerRequestObservationConvention tenantServerRequestObservationConvention() {
        return new DefaultServerRequestObservationConvention() {
            @Override
            public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
                return super.getLowCardinalityKeyValues(context).and(tenantKeyValue(context));
            }
        };
    }

    /**
     * Reads the request attribute JwtAuthenticationFilter sets, not {@code TenantContext}: this
     * observation's tags are computed in ServerHttpObservationFilter's `finally`, which runs
     * *after* JwtAuthenticationFilter's own `finally` has already cleared the TenantContext
     * ThreadLocal (ServerHttpObservationFilter wraps the whole Spring Security filter chain). A
     * request attribute survives that because it lives as long as the request object.
     */
    private KeyValue tenantKeyValue(ServerRequestObservationContext context) {
        Object tenantId = context.getCarrier().getAttribute(JwtAuthenticationFilter.TENANT_ID_REQUEST_ATTRIBUTE);
        return KeyValue.of(TENANT_TAG, tenantId == null ? NO_TENANT : tenantId.toString());
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> tenantCardinalityCustomizer() {
        return registry -> registry.config()
                .meterFilter(MeterFilter.maximumAllowableTags(
                        "http.server.requests", TENANT_TAG, MAX_TRACKED_TENANTS, MeterFilter.deny()))
                .meterFilter(MeterFilter.maximumAllowableTags(
                        RATE_LIMIT_REJECTIONS_METRIC, TENANT_TAG, MAX_TRACKED_TENANTS, MeterFilter.deny()));
    }
}
