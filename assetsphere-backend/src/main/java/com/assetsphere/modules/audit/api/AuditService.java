package com.assetsphere.modules.audit.api;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.assetsphere.modules.audit.domain.AuditRecord;
import com.assetsphere.modules.audit.persistence.AuditRecordRepository;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRecordRepository auditRecordRepository;

    @Transactional
    public void record(UUID actorUserId, AuditAction auditAction, UUID workspaceId, String resourceType,
                       UUID resourceId, String metadata) {
        auditRecordRepository.save(
                AuditRecord.create(
                        actorUserId,
                        auditAction,
                        workspaceId,
                        resourceType,
                        resourceId,
                        MDC.get("correlationId"),
                        metadata
                ));
    }
}
