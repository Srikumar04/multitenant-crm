package com.srikumar.multitenantcrm.tenant;

import com.srikumar.multitenantcrm.common.ResourceNotFoundException;
import com.srikumar.multitenantcrm.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Looks up the authenticated caller's own tenant - `tenants` has no RLS (PROJECT_BRIEF.md
 * section 5), so this is scoped manually to `TenantContext`'s id rather than relying on the
 * database to filter it, the same way RateLimitService resolves a tenant's plan.
 */
@Service
public class TenantSelfService {

    private final TenantRepository tenantRepository;
    private final TransactionTemplate transactionTemplate;
    private final int freeRequestsPerMinute;
    private final int proRequestsPerMinute;

    public TenantSelfService(
            TenantRepository tenantRepository,
            TransactionTemplate transactionTemplate,
            @Value("${app.rate-limit.free-requests-per-minute}") int freeRequestsPerMinute,
            @Value("${app.rate-limit.pro-requests-per-minute}") int proRequestsPerMinute) {
        this.tenantRepository = tenantRepository;
        this.transactionTemplate = transactionTemplate;
        this.freeRequestsPerMinute = freeRequestsPerMinute;
        this.proRequestsPerMinute = proRequestsPerMinute;
    }

    public TenantSelfResponse getCurrentTenant() {
        var tenantId = TenantContext.getTenantId();
        Tenant tenant = transactionTemplate
                .execute(status -> tenantRepository.findById(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " not found"));
        int rateLimit = "pro".equalsIgnoreCase(tenant.getPlan()) ? proRequestsPerMinute : freeRequestsPerMinute;
        return new TenantSelfResponse(tenant.getId(), tenant.getName(), tenant.getPlan(), tenant.getStatus(), rateLimit);
    }
}
