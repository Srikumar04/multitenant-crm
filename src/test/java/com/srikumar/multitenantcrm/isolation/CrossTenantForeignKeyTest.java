package com.srikumar.multitenantcrm.isolation;

import com.srikumar.multitenantcrm.contact.Contact;
import com.srikumar.multitenantcrm.contact.ContactRepository;
import com.srikumar.multitenantcrm.tenant.Tenant;
import com.srikumar.multitenantcrm.tenant.TenantRepository;
import com.srikumar.multitenantcrm.testsupport.AbstractIsolationTest;
import com.srikumar.multitenantcrm.ticket.Ticket;
import com.srikumar.multitenantcrm.ticket.TicketRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the composite foreign key (tenant_id, contact_id) -> contacts(tenant_id, id) rejects a
 * ticket that references a real contact belonging to a different tenant, independent of RLS.
 */
class CrossTenantForeignKeyTest extends AbstractIsolationTest {

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    ContactRepository contactRepository;

    @Autowired
    TicketRepository ticketRepository;

    @Test
    void ticketCannotReferenceAnotherTenantsContact() {
        UUID tenantA = createTenant("Tenant A");
        UUID tenantB = createTenant("Tenant B");
        UUID contactInB = asTenant(tenantB, () -> {
            Contact contact = new Contact();
            contact.setId(UUID.randomUUID());
            contact.setTenantId(tenantB);
            contact.setName("Bob");
            contact.setEmail("bob@example.com");
            return contactRepository.save(contact);
        }).getId();

        Ticket ticket = new Ticket();
        ticket.setId(UUID.randomUUID());
        ticket.setTenantId(tenantA);
        ticket.setContactId(contactInB);
        ticket.setTitle("Help");
        ticket.setStatus("OPEN");
        ticket.setPriority("LOW");

        assertThatThrownBy(() -> asTenant(tenantA, () -> ticketRepository.save(ticket)))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID createTenant(String name) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(name);
        tenant.setPlan("free");
        tenant.setStatus("active");
        return withNoTenant(() -> tenantRepository.save(tenant)).getId();
    }
}
