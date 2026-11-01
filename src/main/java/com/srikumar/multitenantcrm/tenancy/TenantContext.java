package com.srikumar.multitenantcrm.tenancy;

import java.util.UUID;

/**
 * Holds the current request's tenant id for the duration of a thread. Populated from a JWT
 * claim once Phase 2 lands; set directly by tests until then.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
