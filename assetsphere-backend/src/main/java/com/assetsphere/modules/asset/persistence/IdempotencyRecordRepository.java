package com.assetsphere.modules.asset.persistence;

import com.assetsphere.modules.asset.domain.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByUserIdAndWorkspaceIdAndOperationTypeAndIdempotencyKey(
            UUID userId,
            UUID workspaceId,
            String operationType,
            String key
    );
}
