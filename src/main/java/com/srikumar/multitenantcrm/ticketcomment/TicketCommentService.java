package com.srikumar.multitenantcrm.ticketcomment;

import com.srikumar.multitenantcrm.audit.AuditLogService;
import com.srikumar.multitenantcrm.common.ResourceNotFoundException;
import com.srikumar.multitenantcrm.tenancy.CurrentUserContext;
import com.srikumar.multitenantcrm.tenancy.TenantContext;
import com.srikumar.multitenantcrm.ticket.TicketRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TicketCommentService {

    private final TicketCommentRepository ticketCommentRepository;
    private final TicketRepository ticketRepository;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    public TicketCommentService(
            TicketCommentRepository ticketCommentRepository,
            TicketRepository ticketRepository,
            AuditLogService auditLogService,
            TransactionTemplate transactionTemplate) {
        this.ticketCommentRepository = ticketCommentRepository;
        this.ticketRepository = ticketRepository;
        this.auditLogService = auditLogService;
        this.transactionTemplate = transactionTemplate;
    }

    public Page<TicketComment> list(UUID ticketId, Pageable pageable) {
        return transactionTemplate.execute(status -> {
            requireTicket(ticketId);
            return ticketCommentRepository.findByTicketId(ticketId, pageable);
        });
    }

    public TicketComment create(UUID ticketId, TicketCommentRequest request) {
        return transactionTemplate.execute(status -> {
            requireTicket(ticketId);
            TicketComment comment = new TicketComment();
            comment.setId(UUID.randomUUID());
            comment.setTenantId(TenantContext.getTenantId());
            comment.setTicketId(ticketId);
            comment.setAuthorId(CurrentUserContext.getUserId());
            comment.setBody(request.body());
            TicketComment saved = ticketCommentRepository.save(comment);
            auditLogService.record(
                    "CREATE", "TicketComment", saved.getId(), null, TicketCommentResponse.from(saved));
            return saved;
        });
    }

    private void requireTicket(UUID ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new ResourceNotFoundException("Ticket " + ticketId + " not found");
        }
    }
}
