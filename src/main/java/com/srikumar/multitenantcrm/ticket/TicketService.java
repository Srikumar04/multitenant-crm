package com.srikumar.multitenantcrm.ticket;

import com.srikumar.multitenantcrm.audit.AuditLogService;
import com.srikumar.multitenantcrm.common.ResourceNotFoundException;
import com.srikumar.multitenantcrm.tenancy.TenantContext;
import com.srikumar.multitenantcrm.webhook.WebhookDispatchService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final AuditLogService auditLogService;
    private final WebhookDispatchService webhookDispatchService;
    private final TransactionTemplate transactionTemplate;

    public TicketService(
            TicketRepository ticketRepository,
            AuditLogService auditLogService,
            WebhookDispatchService webhookDispatchService,
            TransactionTemplate transactionTemplate) {
        this.ticketRepository = ticketRepository;
        this.auditLogService = auditLogService;
        this.webhookDispatchService = webhookDispatchService;
        this.transactionTemplate = transactionTemplate;
    }

    public Page<Ticket> list(Pageable pageable) {
        return transactionTemplate.execute(status -> ticketRepository.findAll(pageable));
    }

    public Ticket get(UUID id) {
        return transactionTemplate
                .execute(status -> ticketRepository.findById(id))
                .orElseThrow(() -> notFound(id));
    }

    public Ticket create(TicketRequest request) {
        return transactionTemplate.execute(status -> {
            Ticket ticket = new Ticket();
            ticket.setId(UUID.randomUUID());
            ticket.setTenantId(TenantContext.getTenantId());
            ticket.setContactId(request.contactId());
            ticket.setTitle(request.title());
            ticket.setStatus(request.status());
            ticket.setPriority(request.priority());
            ticket.setAssigneeId(request.assigneeId());
            // saveAndFlush, not save: @CreationTimestamp is generated at flush time, and the
            // audit log / webhook payload built right below need the real value, not null.
            Ticket saved = ticketRepository.saveAndFlush(ticket);
            auditLogService.record("CREATE", "Ticket", saved.getId(), null, TicketResponse.from(saved));
            webhookDispatchService.dispatchAfterCommit(saved.getTenantId(), "ticket.created", TicketResponse.from(saved));
            return saved;
        });
    }

    public Ticket update(UUID id, TicketRequest request) {
        return transactionTemplate.execute(status -> {
            Ticket existing = ticketRepository.findById(id).orElseThrow(() -> notFound(id));
            TicketResponse before = TicketResponse.from(existing);
            existing.setContactId(request.contactId());
            existing.setTitle(request.title());
            existing.setStatus(request.status());
            existing.setPriority(request.priority());
            existing.setAssigneeId(request.assigneeId());
            Ticket saved = ticketRepository.save(existing);
            auditLogService.record("UPDATE", "Ticket", saved.getId(), before, TicketResponse.from(saved));
            webhookDispatchService.dispatchAfterCommit(saved.getTenantId(), "ticket.updated", TicketResponse.from(saved));
            return saved;
        });
    }

    public void delete(UUID id) {
        transactionTemplate.execute(status -> {
            Ticket existing = ticketRepository.findById(id).orElseThrow(() -> notFound(id));
            ticketRepository.delete(existing);
            auditLogService.record("DELETE", "Ticket", id, TicketResponse.from(existing), null);
            webhookDispatchService.dispatchAfterCommit(
                    existing.getTenantId(), "ticket.deleted", TicketResponse.from(existing));
            return null;
        });
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Ticket " + id + " not found");
    }
}
