package com.srikumar.multitenantcrm.webhook;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeadLetterRepository extends JpaRepository<WebhookDeadLetter, UUID> {
}
