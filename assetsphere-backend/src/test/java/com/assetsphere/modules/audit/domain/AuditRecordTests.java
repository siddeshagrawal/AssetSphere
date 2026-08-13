package com.assetsphere.modules.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.assetsphere.modules.audit.api.AuditAction;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditRecordTests {

    @Test
    void createsImmutableLedgerEntryWithRequiredAuditContext() {
        UUID actorUserId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        AuditRecord record = AuditRecord.create(
                actorUserId,
                AuditAction.ASSET_UPLOADED,
                workspaceId,
                " ASSET ",
                resourceId,
                "correlation-id",
                "{}"
        );

        assertThat(record.getActorUserId()).isEqualTo(actorUserId);
        assertThat(record.getAction()).isEqualTo(AuditAction.ASSET_UPLOADED);
        assertThat(record.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(record.getResourceType()).isEqualTo("ASSET");
        assertThat(record.getResourceId()).isEqualTo(resourceId);
        assertThat(record.getCorrelationId()).isEqualTo("correlation-id");
        assertThat(record.getMetadata()).isEqualTo("{}");
    }

    @Test
    void rejectsMissingRequiredAuditFields() {
        assertThatIllegalArgumentException().isThrownBy(() -> AuditRecord.create(
                UUID.randomUUID(), null, UUID.randomUUID(), "ASSET", UUID.randomUUID(), null, "{}"
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> AuditRecord.create(
                UUID.randomUUID(), AuditAction.ASSET_UPLOADED, UUID.randomUUID(), " ", UUID.randomUUID(), null, "{}"
        ));
    }
}
