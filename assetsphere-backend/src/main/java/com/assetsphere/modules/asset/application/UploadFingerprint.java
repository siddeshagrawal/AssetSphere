package com.assetsphere.modules.asset.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class UploadFingerprint {

    private static final String OPERATION = "ASSET_UPLOAD";

    public String create(UUID userId, UUID workspaceId, String filename, String mimeType, long size,
                         String checksum, String displayName, String description) {
        return create(userId, workspaceId, OPERATION, null, filename, mimeType, size, checksum, displayName, description);
    }

    public String createVersion(UUID userId, UUID workspaceId, UUID assetId, String filename,
                                String mimeType, long size, String checksum) {
        return create(userId, workspaceId, "ASSET_VERSION_UPLOAD", assetId, filename, mimeType, size, checksum, null, null);
    }

    private String create(UUID userId, UUID workspaceId, String operation, UUID assetId, String filename,
                          String mimeType, long size, String checksum, String displayName, String description) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            appendField(messageDigest, Objects.requireNonNull(userId, "userId is required").toString());
            appendField(messageDigest, Objects.requireNonNull(workspaceId, "workspaceId is required").toString());
            appendField(messageDigest, operation);
            if (assetId != null) {
                appendField(messageDigest, assetId.toString());
            }
            appendField(messageDigest, filename);
            appendField(messageDigest, mimeType);
            appendField(messageDigest, Long.toString(size));
            appendField(messageDigest, checksum);
            appendField(messageDigest, displayName);
            appendField(messageDigest, description);
            return HexFormat.of().formatHex(messageDigest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void appendField(MessageDigest messageDigest, String value) {
        byte[] bytes = normalize(value).getBytes(StandardCharsets.UTF_8);
        messageDigest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        messageDigest.update(bytes);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
