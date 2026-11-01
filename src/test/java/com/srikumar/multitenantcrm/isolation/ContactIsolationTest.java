package com.srikumar.multitenantcrm.isolation;

import com.srikumar.multitenantcrm.contact.Contact;
import com.srikumar.multitenantcrm.contact.ContactRepository;
import com.srikumar.multitenantcrm.tenant.Tenant;
import com.srikumar.multitenantcrm.tenant.TenantRepository;
import com.srikumar.multitenantcrm.testsupport.AbstractIsolationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves Postgres RLS enforces tenant isolation on `contacts` even when the application layer
 * does not add its own `WHERE tenant_id = ...` filter - the policies in
 * db/changelog/007-rls-policies.yaml do the filtering at the database level.
 */
class ContactIsolationTest extends AbstractIsolationTest {

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    ContactRepository contactRepository;

    @Test
    void tenantCannotListAnotherTenantsContacts() {
        UUID tenantA = createTenant("Tenant A");
        UUID tenantB = createTenant("Tenant B");
        UUID contactId = asTenant(tenantB, () -> contactRepository.save(contact(tenantB, "Bob"))).getId();

        List<Contact> visibleToA = asTenant(tenantA, () -> contactRepository.findAll());
        var fetchedById = asTenant(tenantA, () -> contactRepository.findById(contactId));

        assertThat(visibleToA).isEmpty();
        assertThat(fetchedById).isEmpty();
    }

    @Test
    void tenantCannotUpdateAnotherTenantsContact() {
        UUID tenantA = createTenant("Tenant A");
        UUID tenantB = createTenant("Tenant B");
        UUID contactId = asTenant(tenantB, () -> contactRepository.save(contact(tenantB, "Bob"))).getId();

        int rowsUpdated = asTenant(tenantA, () -> contactRepository.renameById(contactId, "Hacked"));

        assertThat(rowsUpdated).isZero();
        Contact unchanged = asTenant(tenantB, () -> contactRepository.findById(contactId)).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Bob");
    }

    @Test
    void tenantCannotDeleteAnotherTenantsContact() {
        UUID tenantA = createTenant("Tenant A");
        UUID tenantB = createTenant("Tenant B");
        UUID contactId = asTenant(tenantB, () -> contactRepository.save(contact(tenantB, "Bob"))).getId();

        int rowsDeleted = asTenant(tenantA, () -> contactRepository.deleteByIdReturningCount(contactId));

        assertThat(rowsDeleted).isZero();
        assertThat(asTenant(tenantB, () -> contactRepository.findById(contactId))).isPresent();
    }

    @Test
    void tenantCannotInsertAContactClaimingAnotherTenantsId() {
        UUID tenantA = createTenant("Tenant A");
        UUID tenantB = createTenant("Tenant B");
        Contact forged = contact(tenantB, "Forged");

        assertThatThrownBy(() -> asTenant(tenantA, () -> contactRepository.save(forged)))
                .isInstanceOf(DataAccessException.class);

        assertThat(asTenant(tenantB, () -> contactRepository.findAll())).isEmpty();
    }

    @Test
    void noTenantSetReturnsZeroRows() {
        UUID tenantA = createTenant("Tenant A");
        asTenant(tenantA, () -> contactRepository.save(contact(tenantA, "Bob")));

        List<Contact> visible = withNoTenant(() -> contactRepository.findAll());

        assertThat(visible).isEmpty();
    }

    private UUID createTenant(String name) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(name);
        tenant.setPlan("free");
        tenant.setStatus("active");
        return withNoTenant(() -> tenantRepository.save(tenant)).getId();
    }

    private Contact contact(UUID tenantId, String name) {
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setTenantId(tenantId);
        contact.setName(name);
        contact.setEmail(name.toLowerCase() + "@example.com");
        return contact;
    }
}
