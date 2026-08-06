package com.assetsphere.modules.audit.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {
}
