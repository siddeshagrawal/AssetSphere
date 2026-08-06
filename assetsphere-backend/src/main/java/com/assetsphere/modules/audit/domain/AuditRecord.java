package com.assetsphere.modules.audit.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import com.assetsphere.modules.common.BaseEntity;

@Getter
@Entity
@Table(name = "audit_records")
public class AuditRecord extends BaseEntity {
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 80) private AuditAction action;
    @Column(name = "workspace_id") private UUID workspaceId;
    @Column(name = "resource_type", nullable = false, length = 80) private String resourceType;
    @Column(name = "resource_id") private UUID resourceId;
    @Column(name = "correlation_id", length = 128) private String correlationId;
    @Column(columnDefinition = "jsonb") private String metadata;

    protected AuditRecord() { }

    public AuditRecord(UUID actorUserId, AuditAction action, UUID workspaceId, String resourceType, UUID resourceId, String correlationId, String metadata) {
        this.actorUserId = actorUserId; this.action = action; this.workspaceId = workspaceId;
        this.resourceType = resourceType; this.resourceId = resourceId; this.correlationId = correlationId; this.metadata = metadata;
    }
}
