package com.srikumar.multitenantcrm.auth;

import java.util.UUID;

/**
 * Tenant is named explicitly rather than inferred from a subdomain - see docs/decisions.md
 * ("login request shape"). Subdomain-based resolution is listed as a later enhancement in
 * PROJECT_BRIEF.md section 3.
 */
public record LoginRequest(UUID tenantId, String email, String password) {
}
