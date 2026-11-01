package com.srikumar.multitenantcrm.contact;

import com.srikumar.multitenantcrm.audit.AuditLogService;
import com.srikumar.multitenantcrm.common.ResourceNotFoundException;
import com.srikumar.multitenantcrm.tenancy.CurrentUserContext;
import com.srikumar.multitenantcrm.tenancy.TenantContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    public ContactService(
            ContactRepository contactRepository,
            AuditLogService auditLogService,
            TransactionTemplate transactionTemplate) {
        this.contactRepository = contactRepository;
        this.auditLogService = auditLogService;
        this.transactionTemplate = transactionTemplate;
    }

    public Page<Contact> list(Pageable pageable) {
        return transactionTemplate.execute(status -> contactRepository.findAll(pageable));
    }

    public Contact get(UUID id) {
        return transactionTemplate
                .execute(status -> contactRepository.findById(id))
                .orElseThrow(() -> notFound(id));
    }

    public Contact create(ContactRequest request) {
        return transactionTemplate.execute(status -> {
            Contact contact = new Contact();
            contact.setId(UUID.randomUUID());
            contact.setTenantId(TenantContext.getTenantId());
            contact.setName(request.name());
            contact.setEmail(request.email());
            contact.setPhone(request.phone());
            contact.setCreatedBy(CurrentUserContext.getUserId());
            Contact saved = contactRepository.save(contact);
            auditLogService.record("CREATE", "Contact", saved.getId(), null, ContactResponse.from(saved));
            return saved;
        });
    }

    public Contact update(UUID id, ContactRequest request) {
        return transactionTemplate.execute(status -> {
            Contact existing = contactRepository.findById(id).orElseThrow(() -> notFound(id));
            ContactResponse before = ContactResponse.from(existing);
            existing.setName(request.name());
            existing.setEmail(request.email());
            existing.setPhone(request.phone());
            Contact saved = contactRepository.save(existing);
            auditLogService.record("UPDATE", "Contact", saved.getId(), before, ContactResponse.from(saved));
            return saved;
        });
    }

    public void delete(UUID id) {
        transactionTemplate.execute(status -> {
            Contact existing = contactRepository.findById(id).orElseThrow(() -> notFound(id));
            contactRepository.delete(existing);
            auditLogService.record("DELETE", "Contact", id, ContactResponse.from(existing), null);
            return null;
        });
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Contact " + id + " not found");
    }
}
