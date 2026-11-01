package com.srikumar.multitenantcrm.tenant;

import java.util.UUID;

public record TenantOnboardingResponse(UUID tenantId, UUID adminUserId) {
}
