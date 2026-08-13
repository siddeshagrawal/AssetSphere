package com.assetsphere.modules.search.persistence;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AssetContentChunkVectorRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public void store(UUID chunkId, float[] vector) {
        String value = java.util.Arrays.toString(vector);
        int updated = jdbc.update("UPDATE asset_content_chunks SET embedding = CAST(:vector AS vector) WHERE id = :id",
                new MapSqlParameterSource().addValue("id", chunkId).addValue("vector", value));
        if (updated != 1) {
            throw new IllegalStateException("Expected one asset content chunk vector update but updated " + updated);
        }
    }
}
