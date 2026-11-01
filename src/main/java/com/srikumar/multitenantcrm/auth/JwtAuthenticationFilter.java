package com.srikumar.multitenantcrm.auth;

import com.srikumar.multitenantcrm.tenancy.CurrentUserContext;
import com.srikumar.multitenantcrm.tenancy.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates both SecurityContext (for @PreAuthorize role checks) and TenantContext (for RLS)
 * from the JWT's tenant_id/role claims, before any controller or transaction runs. A
 * missing/invalid token leaves no Authentication set, which SecurityConfig's
 * `.anyRequest().authenticated()` turns into a 401 for protected endpoints - the "reject
 * requests without a valid tenant" requirement from PROJECT_BRIEF.md section 7 Phase 2.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                authenticate(header.substring(7));
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            CurrentUserContext.clear();
        }
    }

    private void authenticate(String token) {
        try {
            Claims claims = jwtService.parse(token).getPayload();
            UUID tenantId = UUID.fromString(claims.get("tenant_id", String.class));
            UUID userId = UUID.fromString(claims.get("user_id", String.class));
            String role = claims.get("role", String.class);

            TenantContext.setTenantId(tenantId);
            CurrentUserContext.setUserId(userId);
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            var authentication =
                    new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
            CurrentUserContext.clear();
        }
    }
}
