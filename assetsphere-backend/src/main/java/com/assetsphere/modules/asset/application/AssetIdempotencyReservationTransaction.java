package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.asset.domain.IdempotencyRecord;
import com.assetsphere.modules.asset.domain.IdempotencyStatus;
import com.assetsphere.modules.common.exception.ConflictException;
import java.time.Instant;
import java.util.UUID;
import com.assetsphere.modules.asset.persistence.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AssetIdempotencyReservationTransaction {

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    IdempotencyRecord reserve(IdempotencyRecord record) throws DataIntegrityViolationException {
        return idempotencyRecordRepository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void delete(IdempotencyRecord record) {
        idempotencyRecordRepository.delete(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    IdempotencyRecord retry(UUID recordId, Instant inProgressExpiry) {
        IdempotencyRecord idempotencyRecord = idempotencyRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalStateException("Failed idempotency record is missing"));
        if (idempotencyRecord.getStatus() != IdempotencyStatus.FAILED) {
            throw new ConflictException("An upload with this idempotency key is already in progress");
        }
        idempotencyRecord.retry(inProgressExpiry);
        return idempotencyRecord;
    }
}
