package com.srikumar.multitenantcrm.tenant;

import java.util.UUID;

public record TenantSelfResponse(UUID tenantId, String name, String plan, String status, int rateLimitPerMinute) {
}
