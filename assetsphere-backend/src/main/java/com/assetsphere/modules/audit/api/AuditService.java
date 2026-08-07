package com.assetsphere.modules.audit.api;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.assetsphere.modules.audit.domain.AuditRecord;
import com.assetsphere.modules.audit.persistence.AuditRecordRepository;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRecordRepository auditRecordRepository;

    public void record(UUID actorUserId, AuditAction auditAction, UUID workspaceId, String resourceType,
                       UUID resourceId, String metadata) {
        auditRecordRepository.save(new AuditRecord(actorUserId, auditAction, workspaceId, resourceType,
                resourceId, MDC.get("correlationId"), metadata));
    }
}
