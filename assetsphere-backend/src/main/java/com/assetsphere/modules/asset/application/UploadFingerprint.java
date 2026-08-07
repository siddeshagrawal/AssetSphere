package com.assetsphere.modules.asset.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class UploadFingerprint {

    private static final String OPERATION = "ASSET_UPLOAD";

    public String create(UUID userId, UUID workspaceId, String filename, String mimeType, long size,
                         String checksum, String displayName, String description) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            appendField(digest, Objects.requireNonNull(userId, "userId is required").toString());
            appendField(digest, Objects.requireNonNull(workspaceId, "workspaceId is required").toString());
            appendField(digest, OPERATION);
            appendField(digest, filename);
            appendField(digest, mimeType);
            appendField(digest, Long.toString(size));
            appendField(digest, checksum);
            appendField(digest, displayName);
            appendField(digest, description);
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void appendField(MessageDigest digest, String value) {
        byte[] bytes = normalize(value).getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
