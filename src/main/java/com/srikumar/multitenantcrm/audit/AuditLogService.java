package com.srikumar.multitenantcrm.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srikumar.multitenantcrm.tenancy.CurrentUserContext;
import com.srikumar.multitenantcrm.tenancy.TenantContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * `record` is called from within the same transaction as the create/update/delete it's
 * recording - never opens its own, so the audit row either commits with the change or rolls
 * back with it. `list` is the opposite: a standalone read (AuditLogController, ADMIN-only -
 * PROJECT_BRIEF.md section 6), so it wraps its own transaction like every other top-level
 * tenant-scoped read in this codebase (ADR 0004).
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AuditLogService(
            AuditLogRepository auditLogRepository, ObjectMapper objectMapper, TransactionTemplate transactionTemplate) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public Page<AuditLog> list(Pageable pageable) {
        return transactionTemplate.execute(status -> auditLogRepository.findAll(pageable));
    }

    public void record(String action, String entityName, UUID entityId, Object before, Object after) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setTenantId(TenantContext.getTenantId());
        log.setActorId(CurrentUserContext.getUserId());
        log.setAction(action);
        log.setEntity(entityName);
        log.setEntityId(entityId);
        log.setBeforeJson(toJson(before));
        log.setAfterJson(toJson(after));
        auditLogRepository.save(log);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit log payload", e);
        }
    }
}
