package com.assetsphere.modules.search.domain;

import com.assetsphere.modules.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "asset_content_chunks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetContentChunk extends BaseEntity {

    @Column(name = "workspace_id", nullable = false) private UUID workspaceId;
    @Column(name = "asset_id", nullable = false) private UUID assetId;
    @Column(name = "asset_version_id", nullable = false) private UUID assetVersionId;
    @Column(name = "chunk_index", nullable = false) private int chunkIndex;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(name = "character_count", nullable = false) private int characterCount;
    @Column(name = "content_hash", nullable = false, length = 64) private String contentHash;

    public static AssetContentChunk create(UUID workspaceId, UUID assetId, UUID assetVersionId, int chunkIndex, String content) {
        AssetContentChunk chunk = new AssetContentChunk();
        chunk.workspaceId = workspaceId;
        chunk.assetId = assetId;
        chunk.assetVersionId = assetVersionId;
        chunk.chunkIndex = chunkIndex;
        chunk.content = content.strip();
        chunk.characterCount = chunk.content.length();
        chunk.contentHash = sha256(chunk.content);
        return chunk;
    }

    private static String sha256(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
