package com.srikumar.multitenantcrm.idempotency;

import com.srikumar.multitenantcrm.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Runs after JwtAuthenticationFilter (TenantContext is already populated). Scoped per
 * (tenant, key, method, path) - see docs/decisions.md ADR 0008. Caches and replays whatever
 * response came back the first time, including a failure: that's standard idempotency-key
 * semantics (the caller asked for this exact operation to happen once).
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionTemplate transactionTemplate;

    public IdempotencyFilter(IdempotencyKeyRepository idempotencyKeyRepository, TransactionTemplate transactionTemplate) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String idempotencyKey = request.getHeader("Idempotency-Key");
        UUID tenantId = TenantContext.getTenantId();
        if (idempotencyKey == null || !HttpMethod.POST.matches(request.getMethod()) || tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        Optional<IdempotencyKeyRecord> existing = transactionTemplate.execute(status ->
                idempotencyKeyRepository.findByTenantIdAndIdempotencyKeyAndRequestMethodAndRequestPath(
                        tenantId, idempotencyKey, "POST", path));

        if (existing.isPresent()) {
            replay(response, existing.get());
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrappedResponse);
        store(tenantId, idempotencyKey, path, wrappedResponse);
        wrappedResponse.copyBodyToResponse();
    }

    private void replay(HttpServletResponse response, IdempotencyKeyRecord record) throws IOException {
        response.setStatus(record.getResponseStatus());
        response.setContentType("application/json");
        if (record.getResponseBody() != null) {
            response.getWriter().write(record.getResponseBody());
        }
    }

    private void store(UUID tenantId, String idempotencyKey, String path, ContentCachingResponseWrapper wrappedResponse) {
        String body = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
        transactionTemplate.execute(status -> {
            IdempotencyKeyRecord record = new IdempotencyKeyRecord();
            record.setId(UUID.randomUUID());
            record.setTenantId(tenantId);
            record.setIdempotencyKey(idempotencyKey);
            record.setRequestMethod("POST");
            record.setRequestPath(path);
            record.setResponseStatus(wrappedResponse.getStatus());
            record.setResponseBody(body);
            return idempotencyKeyRepository.save(record);
        });
    }
}
