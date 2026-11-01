package com.srikumar.multitenantcrm.contact;

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
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public PagedModel<ContactResponse> list(Pageable pageable) {
        Page<ContactResponse> page = contactService.list(pageable).map(ContactResponse::from);
        return new PagedModel<>(page);
    }

    @GetMapping("/{id}")
    public ContactResponse get(@PathVariable UUID id) {
        return ContactResponse.from(contactService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ContactResponse> create(@Valid @RequestBody ContactRequest request) {
        Contact created = contactService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ContactResponse.from(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ContactResponse update(@PathVariable UUID id, @Valid @RequestBody ContactRequest request) {
        return ContactResponse.from(contactService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        contactService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
