package com.srikumar.multitenantcrm.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srikumar.multitenantcrm.tenancy.CurrentUserContext;
import com.srikumar.multitenantcrm.tenancy.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Called from within the same transaction as the create/update/delete it's recording - never
 * opens its own, so the audit row either commits with the change or rolls back with it.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
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
