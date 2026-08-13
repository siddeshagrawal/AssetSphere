package com.assetsphere.modules.audit.api;

import com.assetsphere.modules.audit.persistence.AuditRecordRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceActivityQuery {
    private final AuditRecordRepository records;

    @Transactional(readOnly = true)
    public WorkspaceActivityPage recent(UUID workspaceId, int page, int size) {
        int boundedSize = Math.max(1, Math.min(size, 50));
        var result = records.findByWorkspaceIdOrderByCreatedAtDesc(
                workspaceId, PageRequest.of(Math.max(0, page), boundedSize));
        var content = result.getContent().stream().map(record -> new WorkspaceActivityResponse(
                record.getId(), record.getActorUserId(), record.getAction(), record.getResourceType(),
                record.getResourceId(), record.getCreatedAt())).toList();
        return new WorkspaceActivityPage(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
