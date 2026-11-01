package com.srikumar.multitenantcrm.tenant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantOnboardingService onboardingService;
    private final TenantSelfService tenantSelfService;

    public TenantController(TenantOnboardingService onboardingService, TenantSelfService tenantSelfService) {
        this.onboardingService = onboardingService;
        this.tenantSelfService = tenantSelfService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TenantOnboardingResponse> onboard(@RequestBody TenantOnboardingRequest request) {
        TenantOnboardingResponse response = onboardingService.onboard(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public TenantSelfResponse me() {
        return tenantSelfService.getCurrentTenant();
    }
}
