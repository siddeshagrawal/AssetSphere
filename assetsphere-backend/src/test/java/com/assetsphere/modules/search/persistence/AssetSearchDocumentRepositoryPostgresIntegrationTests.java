package com.assetsphere.modules.search.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.search.api.SearchIndexCommand;

import java.time.Instant;
import java.sql.Types;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@EnabledIfEnvironmentVariable(named = "ASSETSPHERE_POSTGRES_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AssetSearchDocumentRepositoryPostgresIntegrationTests {
    private JdbcTemplate jdbc;
    private AssetSearchDocumentRepository repository;
    private UUID workspace;
    private UUID otherWorkspace;

    @BeforeAll
    void setUp() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl("jdbc:postgresql://%s:5432/assetsphere".formatted(env("ASSETSPHERE_POSTGRES_HOST")));
        ds.setUser("assetsphere");
        ds.setPassword(env("POSTGRES_PASSWORD"));
        jdbc = new JdbcTemplate(ds);
        repository = new AssetSearchDocumentRepository(new NamedParameterJdbcTemplate(ds), (ClockProvider) () -> Instant.EPOCH);
    }

    @AfterEach
    void cleanUp() {
        if (workspace != null) {
            jdbc.update("DELETE FROM asset_search_documents WHERE workspace_id IN (?,?)", workspace, otherWorkspace);
            jdbc.update("DELETE FROM asset_versions WHERE asset_id IN (SELECT id FROM assets WHERE workspace_id IN (?,?))", workspace, otherWorkspace);
            jdbc.update("DELETE FROM storage_objects WHERE workspace_id IN (?,?)", workspace, otherWorkspace);
            jdbc.update("DELETE FROM assets WHERE workspace_id IN (?,?)", workspace, otherWorkspace);
            jdbc.update("DELETE FROM workspaces WHERE id IN (?,?)", workspace, otherWorkspace);
            workspace = null;
        }
    }

    @Test
    void searchesWeightedFieldsWithWorkspaceIsolationStablePagingAndSafeQueryParameters() {
        workspace = UUID.randomUUID();
        otherWorkspace = UUID.randomUUID();
        insertWorkspace(workspace);
        insertWorkspace(otherWorkspace);
        UUID first = index(workspace, "Display Alpha", "filereference.pdf", "description text", "unique extracted phrase");
        UUID second = index(workspace, "Display Alpha", "annual-report.pdf", null, "");
        index(otherWorkspace, "Other", "other.pdf", null, "unique extracted phrase");
        assertThat(repository.search(workspace, "filereference", 20, 0)).extracting(r -> r.assetId()).contains(first);
        assertThat(repository.search(workspace, "annual report", 20, 0)).hasSize(1);
        assertThat(repository.search(workspace, "unique extracted", 20, 0)).extracting(r -> r.assetId()).containsExactly(first);
        assertThat(repository.search(workspace, "display alpha", 1, 0)).hasSize(1);
        assertThat(repository.search(workspace, "display alpha", 1, 1)).hasSize(1);
        assertThat(repository.search(workspace, "' OR 1=1 --", 20, 0)).isEmpty();
        assertThat(repository.count(workspace, "absent phrase")).isZero();
    }

    private UUID index(UUID ws, String display, String filename, String description, String text) {
        UUID asset = UUID.randomUUID(), version = UUID.randomUUID(), storage = UUID.randomUUID();
        String checksum = UUID.randomUUID().toString().replace("-", "") + "a".repeat(32);
        var now = Instant.EPOCH.atOffset(ZoneOffset.UTC);
        jdbc.update("INSERT INTO storage_objects(id,workspace_id,checksum_sha256,object_key,storage_provider,file_size,mime_type,reference_count,created_at,updated_at,version) VALUES (?,?,?,'test','MINIO',1,'application/pdf',1,?,?,0)", storage, ws, checksum, now, now);
        jdbc.update("INSERT INTO assets(id,workspace_id,owner_user_id,display_name,asset_type,lifecycle_status,processing_status,latest_version_number,created_at,updated_at,version) VALUES (?,?,?,?,'PDF','ACTIVE','READY',1,?,?,0)", asset, ws, UUID.randomUUID(), display, now, now);
        jdbc.update("INSERT INTO asset_versions(id,asset_id,version_number,original_filename,mime_type,file_size,checksum_sha256,storage_object_id,uploaded_by_user_id,processing_status,created_at,updated_at,version) VALUES (?,?,1,?,'application/pdf',1,?,?,?,'READY',?,?,0)", version, asset, filename, "b".repeat(64), storage, UUID.randomUUID(), now, now);
        repository.upsert(new SearchIndexCommand(ws, asset, version, display, filename, description, "application/pdf", "READY", text));
        return asset;
    }

    private void insertWorkspace(UUID id) {
        jdbc.update("INSERT INTO workspaces(id,created_at,updated_at,version,name,slug,status,creator_user_id) VALUES (?,?,?,?,?,?, 'ACTIVE',?)", statement -> {
            statement.setObject(1, id);
            statement.setObject(2, Instant.EPOCH.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setObject(3, Instant.EPOCH.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setLong(4, 0);
            statement.setString(5, "Search");
            statement.setString(6, "search-" + id);
            statement.setObject(7, UUID.randomUUID());
        });
    }

    private String env(String n) {
        String v = System.getenv(n);
        if (v == null || v.isBlank()) throw new IllegalStateException(n + " required");
        return v;
    }
}
