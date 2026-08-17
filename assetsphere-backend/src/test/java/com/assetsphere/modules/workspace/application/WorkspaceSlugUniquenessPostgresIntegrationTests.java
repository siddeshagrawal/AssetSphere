package com.assetsphere.modules.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(named = "ASSETSPHERE_POSTGRES_INTEGRATION", matches = "true")
class WorkspaceSlugUniquenessPostgresIntegrationTests {

    @Test
    void workspaceSlugIsUniqueWithinCreatorNamespaceOnly() {
        JdbcTemplate jdbc = new JdbcTemplate(migratedDataSource());
        UUID creatorA = UUID.randomUUID();
        UUID creatorB = UUID.randomUUID();
        UUID workspaceA = UUID.randomUUID();
        UUID workspaceB = UUID.randomUUID();
        String slug = "shared-" + UUID.randomUUID();
        try {
            insertWorkspace(jdbc, workspaceA, creatorA, slug);
            insertWorkspace(jdbc, workspaceB, creatorB, slug);

            assertThatThrownBy(() -> insertWorkspace(jdbc, UUID.randomUUID(), creatorA, slug))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("""
                    SELECT pg_get_constraintdef(oid)
                      FROM pg_constraint
                     WHERE conrelid = 'workspaces'::regclass
                       AND conname = 'uk_workspaces_creator_slug'
                    """, String.class)).isEqualTo("UNIQUE (creator_user_id, slug)");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*)
                      FROM pg_constraint
                     WHERE conrelid = 'workspaces'::regclass
                       AND conname = 'workspaces_slug_key'
                    """, Integer.class)).isZero();
        } finally {
            jdbc.update("DELETE FROM workspaces WHERE id IN (?, ?)", workspaceA, workspaceB);
        }
    }

    private void insertWorkspace(JdbcTemplate jdbc, UUID workspaceId, UUID creatorId, String slug) {
        jdbc.update("""
                INSERT INTO workspaces
                    (id, created_at, updated_at, version, name, slug, status, creator_user_id)
                VALUES (?, now(), now(), 0, 'Slug scope test', ?, 'ACTIVE', ?)
                """, workspaceId, slug, creatorId);
    }

    private PGSimpleDataSource migratedDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl("jdbc:postgresql://%s:5432/assetsphere"
                .formatted(requiredEnvironment("ASSETSPHERE_POSTGRES_HOST")));
        dataSource.setUser("assetsphere");
        dataSource.setPassword(requiredEnvironment("POSTGRES_PASSWORD"));
        Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .defaultSchema("public")
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return dataSource;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
