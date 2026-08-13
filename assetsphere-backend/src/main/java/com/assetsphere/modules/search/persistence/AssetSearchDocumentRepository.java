package com.assetsphere.modules.search.persistence;

import com.assetsphere.modules.search.api.AssetSearchResult;
import com.assetsphere.modules.search.api.SearchIndexCommand;
import com.assetsphere.modules.common.time.ClockProvider;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AssetSearchDocumentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ClockProvider clockProvider;

    public void upsert(SearchIndexCommand command) {
        OffsetDateTime now = clockProvider.now().atOffset(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO asset_search_documents (
                    asset_version_id, asset_id, workspace_id, display_name, original_filename, description,
                    mime_type, processing_status, extracted_text, created_at, updated_at
                ) VALUES (
                    :assetVersionId, :assetId, :workspaceId, :displayName, :originalFilename, :description,
                    :mimeType, :processingStatus, :extractedText, :now, :now
                )
                ON CONFLICT (asset_version_id) DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    original_filename = EXCLUDED.original_filename,
                    description = EXCLUDED.description,
                    mime_type = EXCLUDED.mime_type,
                    processing_status = EXCLUDED.processing_status,
                    extracted_text = EXCLUDED.extracted_text,
                    updated_at = EXCLUDED.updated_at
                """, parameters(command).addValue("now", now, Types.TIMESTAMP_WITH_TIMEZONE));
    }

    public List<AssetSearchResult> search(UUID workspaceId, String query, int size, long offset) {
        return jdbcTemplate.query("""
                SELECT d.asset_id, d.asset_version_id, v.version_number, d.display_name, d.original_filename, d.mime_type, d.processing_status,
                       ts_rank(search_vector, websearch_to_tsquery('simple', :query)) AS rank,
                       COALESCE(NULLIF(ts_headline('simple', extracted_text, websearch_to_tsquery('simple', :query),
                           'MaxWords=20, MinWords=10, MaxFragments=1'), ''), '') AS snippet
                  FROM asset_search_documents d
                  JOIN asset_versions v ON v.id = d.asset_version_id
                  JOIN assets a ON a.id = d.asset_id
                               AND a.workspace_id = d.workspace_id
                               AND a.latest_version_number = v.version_number
                 WHERE d.workspace_id = :workspaceId
                   AND search_vector @@ websearch_to_tsquery('simple', :query)
                 ORDER BY rank DESC, d.asset_id ASC
                 LIMIT :size OFFSET :offset
                """, new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("query", query)
                .addValue("size", size)
                .addValue("offset", offset), (resultSet, rowNum) -> new AssetSearchResult(
                resultSet.getObject("asset_id", UUID.class),
                resultSet.getObject("asset_version_id", UUID.class),
                resultSet.getInt("version_number"),
                resultSet.getString("display_name"),
                resultSet.getString("original_filename"),
                resultSet.getString("mime_type"),
                resultSet.getString("processing_status"),
                resultSet.getDouble("rank"),
                resultSet.getString("snippet")
        ));
    }

    public long count(UUID workspaceId, String query) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM asset_search_documents d
                  JOIN asset_versions v ON v.id = d.asset_version_id
                  JOIN assets a ON a.id = d.asset_id
                               AND a.workspace_id = d.workspace_id
                               AND a.latest_version_number = v.version_number
                 WHERE d.workspace_id = :workspaceId
                   AND d.search_vector @@ websearch_to_tsquery('simple', :query)
                """, new MapSqlParameterSource().addValue("workspaceId", workspaceId).addValue("query", query), Long.class);
        return value == null ? 0 : value;
    }

    public List<AssetSearchResult> semanticSearch(UUID workspaceId, float[] embedding, int size) {
        String vector = java.util.Arrays.toString(embedding);
        List<AssetSearchResult> results = jdbcTemplate.query("""
                SELECT * FROM (
                    SELECT DISTINCT ON (c.asset_id) c.asset_id, c.asset_version_id, v.version_number, d.display_name, d.original_filename,
                           d.mime_type, d.processing_status, 1 - (c.embedding <=> CAST(:embedding AS vector)) AS similarity,
                           c.content AS snippet
                      FROM asset_content_chunks c
                      JOIN asset_semantic_indexes i ON i.asset_version_id = c.asset_version_id AND i.status = 'READY'
                      LEFT JOIN asset_search_documents d ON d.asset_version_id = c.asset_version_id
                      JOIN asset_versions v ON v.id = c.asset_version_id
                      JOIN assets a ON a.id = c.asset_id
                                   AND a.workspace_id = c.workspace_id
                                   AND a.latest_version_number = v.version_number
                     WHERE c.workspace_id = :workspaceId AND c.embedding IS NOT NULL
                     ORDER BY c.asset_id, c.embedding <=> CAST(:embedding AS vector), c.asset_version_id ASC, c.chunk_index ASC
                ) candidates
                ORDER BY similarity DESC, asset_id ASC
                LIMIT :size
                """, new MapSqlParameterSource().addValue("workspaceId", workspaceId).addValue("embedding", vector).addValue("size", size),
                (resultSet, row) -> new AssetSearchResult(resultSet.getObject("asset_id", UUID.class), resultSet.getObject("asset_version_id", UUID.class),
                        resultSet.getInt("version_number"),
                        resultSet.getString("display_name"), resultSet.getString("original_filename"), resultSet.getString("mime_type"),
                        resultSet.getString("processing_status"), resultSet.getDouble("similarity"), resultSet.getString("snippet")));
        log.info("Semantic retrieval completed workspaceId={} candidateLimit={} rowsReturned={}",
                workspaceId, size, results.size());
        return results;
    }

    public Optional<String> findExtractedText(UUID workspaceId, UUID assetVersionId) {
        List<String> values = jdbcTemplate.query("""
                SELECT extracted_text FROM asset_search_documents
                 WHERE workspace_id = :workspaceId AND asset_version_id = :assetVersionId
                """, new MapSqlParameterSource().addValue("workspaceId", workspaceId)
                .addValue("assetVersionId", assetVersionId), (resultSet, rowNum) -> resultSet.getString(1));
        return values.stream().findFirst();
    }

    public void updateMetadata(UUID workspaceId, UUID assetId, String displayName, String description) {
        jdbcTemplate.update("""
                UPDATE asset_search_documents
                   SET display_name = :displayName, description = :description, updated_at = :now
                 WHERE workspace_id = :workspaceId AND asset_id = :assetId
                """, new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("assetId", assetId)
                .addValue("displayName", displayName)
                .addValue("description", description)
                .addValue("now", clockProvider.now().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE));
    }

    private MapSqlParameterSource parameters(SearchIndexCommand command) {
        return new MapSqlParameterSource()
                .addValue("workspaceId", command.workspaceId())
                .addValue("assetId", command.assetId())
                .addValue("assetVersionId", command.assetVersionId())
                .addValue("displayName", command.displayName())
                .addValue("originalFilename", command.originalFilename())
                .addValue("description", command.description())
                .addValue("mimeType", command.mimeType())
                .addValue("processingStatus", command.processingStatus())
                .addValue("extractedText", command.extractedText());
    }
}
