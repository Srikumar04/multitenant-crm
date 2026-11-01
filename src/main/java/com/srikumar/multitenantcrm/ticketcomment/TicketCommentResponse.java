package com.srikumar.multitenantcrm.ticketcomment;

import java.time.Instant;
import java.util.UUID;

public record TicketCommentResponse(UUID id, UUID ticketId, UUID authorId, String body, Instant createdAt) {

    public static TicketCommentResponse from(TicketComment comment) {
        return new TicketCommentResponse(
                comment.getId(),
                comment.getTicketId(),
                comment.getAuthorId(),
                comment.getBody(),
                comment.getCreatedAt());
    }
}
