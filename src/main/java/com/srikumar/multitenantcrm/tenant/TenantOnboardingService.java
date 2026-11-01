package com.srikumar.multitenantcrm.tenant;

import com.srikumar.multitenantcrm.tenancy.TenantContext;
import com.srikumar.multitenantcrm.user.User;
import com.srikumar.multitenantcrm.user.UserRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates a tenant and its first ADMIN user atomically. The tenant id is generated up front so
 * TenantContext can be set to it before the transaction begins - required for the new admin
 * user row to satisfy the RLS WITH CHECK policy on `users` (PROJECT_BRIEF.md section 4).
 */
@Service
public class TenantOnboardingService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    public TenantOnboardingService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
    }

    public TenantOnboardingResponse onboard(TenantOnboardingRequest request) {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        try {
            return transactionTemplate.execute(status -> {
                Tenant tenant = new Tenant();
                tenant.setId(tenantId);
                tenant.setName(request.name());
                tenant.setPlan(request.plan());
                tenant.setStatus("active");
                tenantRepository.save(tenant);

                User admin = new User();
                admin.setId(UUID.randomUUID());
                admin.setTenantId(tenantId);
                admin.setEmail(request.adminEmail());
                admin.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
                admin.setRole("ADMIN");
                userRepository.save(admin);

                return new TenantOnboardingResponse(tenantId, admin.getId());
            });
        } finally {
            TenantContext.clear();
        }
    }
}
