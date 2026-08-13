package com.assetsphere.modules.audit.domain;

import com.assetsphere.modules.audit.api.AuditAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import com.assetsphere.modules.common.persistence.BaseEntity;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditRecord extends BaseEntity {

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private AuditAction action;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "resource_type", nullable = false, length = 80)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column
    private String metadata;

    public static AuditRecord create(
            UUID actorUserId,
            AuditAction action,
            UUID workspaceId,
            String resourceType,
            UUID resourceId,
            String correlationId,
            String metadata
    ) {
        if (action == null) {
            throw new IllegalArgumentException("Audit action is required");
        }
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("Audit resource type is required");
        }

        AuditRecord record = new AuditRecord();
        record.actorUserId = actorUserId;
        record.action = action;
        record.workspaceId = workspaceId;
        record.resourceType = resourceType.trim();
        record.resourceId = resourceId;
        record.correlationId = correlationId;
        record.metadata = metadata;
        return record;
    }

}
