package com.srikumar.multitenantcrm.ticketcomment;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
public class TicketCommentController {

    private final TicketCommentService ticketCommentService;

    public TicketCommentController(TicketCommentService ticketCommentService) {
        this.ticketCommentService = ticketCommentService;
    }

    @GetMapping
    public PagedModel<TicketCommentResponse> list(@PathVariable UUID ticketId, Pageable pageable) {
        Page<TicketCommentResponse> page =
                ticketCommentService.list(ticketId, pageable).map(TicketCommentResponse::from);
        return new PagedModel<>(page);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<TicketCommentResponse> create(
            @PathVariable UUID ticketId, @Valid @RequestBody TicketCommentRequest request) {
        TicketComment created = ticketCommentService.create(ticketId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketCommentResponse.from(created));
    }
}
