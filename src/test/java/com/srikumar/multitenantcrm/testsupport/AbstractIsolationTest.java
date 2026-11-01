package com.srikumar.multitenantcrm.testsupport;

import com.srikumar.multitenantcrm.tenancy.TenantContext;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.util.UUID;

/**
 * Base for isolation tests: runs against a real Postgres container seeded with the same
 * app_migrator / app_runtime role split used in docker-compose (db/init/001-roles.sql), so
 * Liquibase runs as the table-owning migrator and the app runs as the non-owning runtime role
 * that Postgres RLS actually restricts.
 *
 * <p>The container is started once in a static initializer (singleton-container pattern) rather
 * than via {@code @Testcontainers}/{@code @Container}, which would stop and restart it - on a new
 * port - around every test class, leaving a stale pooled connection (and a stale baked-in JDBC
 * URL in a cached Spring context) pointing at the old container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class AbstractIsolationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("multitenant_crm")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("db/init/001-roles.sql"),
                    "/docker-entrypoint-initdb.d/001-roles.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "app_runtime");
        registry.add("spring.datasource.password", () -> "app_runtime");
        registry.add("spring.liquibase.user", () -> "app_migrator");
        registry.add("spring.liquibase.password", () -> "app_migrator");
    }

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    protected <T> T asTenant(UUID tenantId, Supplier<T> work) {
        TenantContext.setTenantId(tenantId);
        try {
            return transactionTemplate.execute(status -> work.get());
        } finally {
            TenantContext.clear();
        }
    }

    protected void asTenant(UUID tenantId, Runnable work) {
        asTenant(tenantId, () -> {
            work.run();
            return null;
        });
    }

    protected <T> T withNoTenant(Supplier<T> work) {
        TenantContext.clear();
        return transactionTemplate.execute(status -> work.get());
    }
}
