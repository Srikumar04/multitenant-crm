package com.srikumar.multitenantcrm.tenant;

public record TenantOnboardingRequest(String name, String plan, String adminEmail, String adminPassword) {
}
