package com.srikumar.multitenantcrm.idempotency;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyRecord, UUID> {

    Optional<IdempotencyKeyRecord> findByTenantIdAndIdempotencyKeyAndRequestMethodAndRequestPath(
            UUID tenantId, String idempotencyKey, String requestMethod, String requestPath);
}
