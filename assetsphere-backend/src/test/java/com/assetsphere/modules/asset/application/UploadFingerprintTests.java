package com.assetsphere.modules.asset.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UploadFingerprintTests {

    private final UploadFingerprint fingerprint = new UploadFingerprint();
    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();

    @Test
    void producesSameFingerprintForSameLogicalRequest() {
        assertThat(create(userId, workspaceId, "report.pdf", "application/pdf", 42, "checksum", "Report", "Description"))
                .isEqualTo(create(userId, workspaceId, "report.pdf", "application/pdf", 42, "checksum", "Report", "Description"));
    }

    @Test
    void changesWhenAnyBusinessSignificantFieldChanges() {
        String baseline = create(userId, workspaceId, "report.pdf", "application/pdf", 42, "checksum", "Report", "Description");

        assertThat(create(UUID.randomUUID(), workspaceId, "report.pdf", "application/pdf", 42, "checksum", "Report", "Description")).isNotEqualTo(baseline);
        assertThat(create(userId, UUID.randomUUID(), "report.pdf", "application/pdf", 42, "checksum", "Report", "Description")).isNotEqualTo(baseline);
        assertThat(create(userId, workspaceId, "other.pdf", "application/pdf", 42, "checksum", "Report", "Description")).isNotEqualTo(baseline);
        assertThat(create(userId, workspaceId, "report.pdf", "image/png", 42, "checksum", "Report", "Description")).isNotEqualTo(baseline);
        assertThat(create(userId, workspaceId, "report.pdf", "application/pdf", 43, "checksum", "Report", "Description")).isNotEqualTo(baseline);
        assertThat(create(userId, workspaceId, "report.pdf", "application/pdf", 42, "other-checksum", "Report", "Description")).isNotEqualTo(baseline);
        assertThat(create(userId, workspaceId, "report.pdf", "application/pdf", 42, "checksum", "Other report", "Description")).isNotEqualTo(baseline);
        assertThat(create(userId, workspaceId, "report.pdf", "application/pdf", 42, "checksum", "Report", "Other description")).isNotEqualTo(baseline);
    }

    @Test
    void avoidsDelimiterBasedCanonicalizationCollision() {
        String first = create(userId, workspaceId, "alpha|beta", "gamma", 42, "checksum", "Report", "Description");
        String second = create(userId, workspaceId, "alpha", "beta|gamma", 42, "checksum", "Report", "Description");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void normalizesNullAndBlankOptionalValuesConsistently() {
        String nullValues = create(userId, workspaceId, "report.pdf", "application/pdf", 42, "checksum", null, null);
        String blankValues = create(userId, workspaceId, "report.pdf", "application/pdf", 42, "checksum", "  ", "\t");

        assertThat(nullValues).isEqualTo(blankValues);
    }

    private String create(
            UUID user,
            UUID workspace,
            String filename,
            String mimeType,
            long size,
            String checksum,
            String displayName,
            String description
    ) {
        return fingerprint.create(user, workspace, filename, mimeType, size, checksum, displayName, description);
    }
}
