package com.srikumar.multitenantcrm.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Right after every transaction begins, binds the session to the current tenant with
 * {@code SET LOCAL app.current_tenant} (via set_config, which is a normal parameterized function
 * call rather than a literal SET statement) so Postgres RLS policies can filter on it. Uses
 * SET LOCAL (transaction-scoped), not plain SET, because Hikari reuses pooled connections across
 * transactions.
 *
 * <p>Forces immediate connection acquisition at transaction start (overriding Hibernate's default
 * lazy acquisition) so the tenant binding always happens before any application query runs -
 * required for RLS to fail closed rather than momentarily running unfiltered.
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    public TenantAwareJpaTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        EntityManagerHolder emHolder = (EntityManagerHolder)
                TransactionSynchronizationManager.getResource(getEntityManagerFactory());
        if (emHolder == null) {
            return;
        }

        EntityManager entityManager = emHolder.getEntityManager();
        UUID tenantId = TenantContext.getTenantId();
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement =
                    connection.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
                statement.setString(1, tenantId != null ? tenantId.toString() : "");
                statement.execute();
            }
        });
    }
}
