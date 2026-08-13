package com.assetsphere.modules.audit.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.assetsphere.modules.audit.domain.AuditRecord;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {
    Page<AuditRecord> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);
}
