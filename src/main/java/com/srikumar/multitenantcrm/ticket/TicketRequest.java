package com.srikumar.multitenantcrm.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TicketRequest(
        @NotNull UUID contactId,
        @NotBlank String title,
        @NotBlank String status,
        @NotBlank String priority,
        UUID assigneeId) {
}
