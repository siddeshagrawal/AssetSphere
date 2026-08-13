package com.assetsphere.modules.storage.application;

import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.storage.api.AssetStorage;
import com.assetsphere.modules.storage.api.StorageFacade;
import com.assetsphere.modules.storage.persistence.StorageObjectRepository;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "ASSETSPHERE_POSTGRES_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StorageApplicationServicePostgresIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private StorageApplicationService storage;
    private final List<UUID> workspaceIds = new ArrayList<>();

    @BeforeAll
    void setUp() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl("jdbc:postgresql://%s:5432/assetsphere".formatted(requiredEnvironment("ASSETSPHERE_POSTGRES_HOST")));
        dataSource.setUser("assetsphere");
        dataSource.setPassword(requiredEnvironment("POSTGRES_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        ClockProvider clock = () -> NOW;
        storage = new StorageApplicationService(
                mock(StorageObjectRepository.class), new NamedParameterJdbcTemplate(dataSource), mock(AssetStorage.class), clock,
                () -> Optional.of(new UUID(0L, 0L))
        );
    }

    @AfterEach
    void cleanUp() {
        workspaceIds.forEach(workspaceId -> {
            jdbc.update("DELETE FROM storage_objects WHERE workspace_id = ?", workspaceId);
            jdbc.update("DELETE FROM workspaces WHERE id = ?", workspaceId);
        });
        workspaceIds.clear();
    }

    @Test
    void attachesUsingPostgresTimestamptzUpsertAndWorkspaceScopedConflictTarget() {
        UUID firstWorkspaceId = insertWorkspace("first");
        UUID secondWorkspaceId = insertWorkspace("second");
        String checksum = "a".repeat(64);

        StorageFacade.StorageObjectReference first = attach(prepared(firstWorkspaceId, checksum));
        Map<String, Object> firstRow = jdbc.queryForMap(
                "SELECT id, reference_count, created_at, updated_at FROM storage_objects WHERE workspace_id = ? AND checksum_sha256 = ?",
                firstWorkspaceId, checksum
        );
        StorageFacade.StorageObjectReference second = attach(prepared(firstWorkspaceId, checksum));
        StorageFacade.StorageObjectReference differentWorkspace = attach(prepared(secondWorkspaceId, checksum));

        assertThat(first.created()).isTrue();
        assertThat(firstRow.get("id")).isEqualTo(first.storageObjectId());
        assertThat(firstRow.get("reference_count")).isEqualTo(1);
        assertThat(firstRow.get("created_at")).isNotNull();
        assertThat(firstRow.get("updated_at")).isNotNull();
        assertThat(second.storageObjectId()).isEqualTo(first.storageObjectId());
        assertThat(second.created()).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT reference_count FROM storage_objects WHERE id = ?", Integer.class, first.storageObjectId()
        )).isEqualTo(2);
        assertThat(differentWorkspace.storageObjectId()).isNotEqualTo(first.storageObjectId());
        assertThat(jdbc.queryForObject(
                "SELECT reference_count FROM storage_objects WHERE id = ?", Integer.class, differentWorkspace.storageObjectId()
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM storage_objects WHERE checksum_sha256 = ?", Integer.class, checksum
        )).isEqualTo(2);
    }

    private StorageFacade.StorageObjectReference attach(StorageFacade.PreparedStorageObject prepared) {
        return transactions.execute(status -> storage.attach(prepared));
    }

    private UUID insertWorkspace(String suffix) {
        UUID workspaceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workspaces (id, created_at, updated_at, version, name, slug, status, creator_user_id)
                VALUES (?, ?, ?, 0, ?, ?, 'ACTIVE', ?)
                """, statement -> {
            statement.setObject(1, workspaceId);
            statement.setObject(2, NOW.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setObject(3, NOW.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setString(4, "Integration " + suffix);
            statement.setString(5, "integration-" + workspaceId);
            statement.setObject(6, UUID.randomUUID());
        });
        workspaceIds.add(workspaceId);
        return workspaceId;
    }

    private StorageFacade.PreparedStorageObject prepared(UUID workspaceId, String checksum) {
        return new StorageFacade.PreparedStorageObject(
                workspaceId,
                checksum,
                "workspaces/%s/objects/%s".formatted(workspaceId, checksum),
                "workspaces/%s/tmp/%s/%s".formatted(workspaceId, UUID.randomUUID(), checksum),
                "MINIO",
                "application/pdf",
                1,
                true,
                false
        );
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured for the PostgreSQL integration test");
        }
        return value;
    }
}
