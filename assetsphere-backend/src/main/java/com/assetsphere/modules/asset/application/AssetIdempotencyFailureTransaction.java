package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.asset.domain.IdempotencyRecord;
import com.assetsphere.modules.asset.domain.IdempotencyStatus;
import com.assetsphere.modules.asset.persistence.IdempotencyRecordRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AssetIdempotencyFailureTransaction {

    private final IdempotencyRecordRepository idempotencyRecords;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markFailed(UUID recordId, Instant failedExpiry) {
        idempotencyRecords.findById(recordId)
                .filter(record -> record.getStatus() == IdempotencyStatus.IN_PROGRESS)
                .ifPresent(record -> record.markFailed(failedExpiry));
    }
}
