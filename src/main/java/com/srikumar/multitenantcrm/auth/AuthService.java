package com.srikumar.multitenantcrm.auth;

import com.srikumar.multitenantcrm.tenancy.TenantContext;
import com.srikumar.multitenantcrm.user.User;
import com.srikumar.multitenantcrm.user.UserRepository;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Looks up the user inside an explicit transaction (TransactionTemplate) rather than relying on
 * Spring Data's implicit per-method transaction for the derived query method: that implicit
 * transaction does not reliably route through TenantAwareJpaTransactionManager, so the RLS
 * session var never gets set and the lookup silently finds nothing. See docs/decisions.md.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private final TransactionTemplate transactionTemplate;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.transactionTemplate = transactionTemplate;
    }

    public String login(UUID tenantId, String email, String rawPassword) {
        TenantContext.setTenantId(tenantId);
        try {
            User user = transactionTemplate.execute(status ->
                    userRepository.findByTenantIdAndEmail(tenantId, email).orElse(null));
            if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
                throw new BadCredentialsException("Invalid credentials");
            }
            return jwtService.generateToken(tenantId, user.getId(), user.getRole(), user.getEmail());
        } finally {
            TenantContext.clear();
        }
    }
}
