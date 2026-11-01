package com.srikumar.multitenantcrm.ticket;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public PagedModel<TicketResponse> list(Pageable pageable) {
        Page<TicketResponse> page = ticketService.list(pageable).map(TicketResponse::from);
        return new PagedModel<>(page);
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable UUID id) {
        return TicketResponse.from(ticketService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketRequest request) {
        Ticket created = ticketService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.from(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public TicketResponse update(@PathVariable UUID id, @Valid @RequestBody TicketRequest request) {
        return TicketResponse.from(ticketService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ticketService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
