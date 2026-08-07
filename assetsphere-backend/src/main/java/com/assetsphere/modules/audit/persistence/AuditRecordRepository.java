package com.assetsphere.modules.audit.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.assetsphere.modules.audit.domain.AuditRecord;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {
}
