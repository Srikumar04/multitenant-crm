package com.srikumar.multitenantcrm.ticketcomment;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {

    Page<TicketComment> findByTicketId(UUID ticketId, Pageable pageable);
}
