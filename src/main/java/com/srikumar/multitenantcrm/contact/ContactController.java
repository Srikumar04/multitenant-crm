package com.srikumar.multitenantcrm.contact;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal read endpoint whose only purpose in Phase 2 is to exercise the full HTTP -> JWT filter
 * -> TenantContext -> RLS chain end to end. Full CRUD (validation, pagination, RFC 7807 errors,
 * audit logging) is Phase 3 scope per PROJECT_BRIEF.md section 7.
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactRepository contactRepository;

    public ContactController(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @GetMapping
    public List<Contact> list() {
        return contactRepository.findAll();
    }
}
