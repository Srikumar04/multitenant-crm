package com.srikumar.multitenantcrm.ticket;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        UUID contactId,
        String title,
        String status,
        String priority,
        UUID assigneeId,
        Instant createdAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getContactId(),
                ticket.getTitle(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getAssigneeId(),
                ticket.getCreatedAt());
    }
}
