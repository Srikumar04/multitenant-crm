package com.srikumar.multitenantcrm.ratelimit;

import com.srikumar.multitenantcrm.tenant.TenantRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One token bucket per tenant, sized by the tenant's plan (PROJECT_BRIEF.md section 9 example
 * numbers). Plain in-memory map, not Redis - fine for a single instance; see docs/decisions.md.
 */
@Service
public class RateLimitService {

    private final ConcurrentHashMap<UUID, Bucket> buckets = new ConcurrentHashMap<>();
    private final TenantRepository tenantRepository;
    private final TransactionTemplate transactionTemplate;
    private final int freeRequestsPerMinute;
    private final int proRequestsPerMinute;

    public RateLimitService(
            TenantRepository tenantRepository,
            TransactionTemplate transactionTemplate,
            @Value("${app.rate-limit.free-requests-per-minute}") int freeRequestsPerMinute,
            @Value("${app.rate-limit.pro-requests-per-minute}") int proRequestsPerMinute) {
        this.tenantRepository = tenantRepository;
        this.transactionTemplate = transactionTemplate;
        this.freeRequestsPerMinute = freeRequestsPerMinute;
        this.proRequestsPerMinute = proRequestsPerMinute;
    }

    public ConsumptionProbe tryConsume(UUID tenantId) {
        Bucket bucket = buckets.computeIfAbsent(tenantId, this::newBucketFor);
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private Bucket newBucketFor(UUID tenantId) {
        String plan = transactionTemplate.execute(
                status -> tenantRepository.findById(tenantId).map(t -> t.getPlan()).orElse("free"));
        int limit = "pro".equalsIgnoreCase(plan) ? proRequestsPerMinute : freeRequestsPerMinute;
        Bandwidth bandwidth = Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(bandwidth).build();
    }
}
