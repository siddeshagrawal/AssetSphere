package com.assetsphere.modules.audit.api;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceActivityResponse(UUID id, UUID actorUserId, AuditAction action,
                                        String resourceType, UUID resourceId, Instant occurredAt) { }
