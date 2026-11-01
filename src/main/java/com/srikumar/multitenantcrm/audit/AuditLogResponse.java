package com.srikumar.multitenantcrm.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorId,
        String action,
        String entity,
        UUID entityId,
        String beforeJson,
        String afterJson,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorId(),
                log.getAction(),
                log.getEntity(),
                log.getEntityId(),
                log.getBeforeJson(),
                log.getAfterJson(),
                log.getCreatedAt());
    }
}
