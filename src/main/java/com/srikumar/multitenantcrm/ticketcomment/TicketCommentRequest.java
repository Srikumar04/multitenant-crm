package com.srikumar.multitenantcrm.ticketcomment;

import jakarta.validation.constraints.NotBlank;

public record TicketCommentRequest(@NotBlank String body) {
}
