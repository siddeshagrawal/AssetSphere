package com.assetsphere.modules.audit;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.assetsphere.modules.audit.domain.AuditAction;
import com.assetsphere.modules.audit.domain.AuditRecord;
import com.assetsphere.modules.audit.domain.AuditRecordRepository;

@Service
public class AuditService {
    private final AuditRecordRepository repository;

    public AuditService(AuditRecordRepository repository) { this.repository = repository; }

    public void record(UUID actorUserId, AuditAction action, UUID workspaceId, String resourceType, UUID resourceId, String metadata) {
        repository.save(new AuditRecord(actorUserId, action, workspaceId, resourceType, resourceId, MDC.get("correlationId"), metadata));
    }
}
