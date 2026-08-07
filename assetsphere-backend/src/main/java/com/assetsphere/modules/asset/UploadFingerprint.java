package com.assetsphere.modules.asset;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class UploadFingerprint {
    public String create(UUID userId, UUID workspaceId, String filename, String mimeType, long size, String checksum, String displayName, String description) {
        String value = String.join("|", userId.toString(), workspaceId.toString(), "ASSET_UPLOAD", value(filename), value(mimeType), Long.toString(size), value(checksum), value(displayName), value(description));
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private String value(String value) { return value == null ? "" : value.trim(); }
}
