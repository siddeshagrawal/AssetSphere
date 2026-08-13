package com.assetsphere.modules.processing.outbox.application;

import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.processing.api.ProcessingProperties;
import com.assetsphere.modules.processing.outbox.persistence.OutboxClaim;
import com.assetsphere.modules.processing.outbox.persistence.OutboxEventClaimRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class OutboxClaimingService {

    private final OutboxEventClaimRepository outboxEventClaimRepository;
    private final ProcessingProperties properties;
    private final ClockProvider clockProvider;
    private final String publisherIdentity = UUID.randomUUID().toString();

    @Transactional
    public List<OutboxClaim> claimAvailable() {
        Instant now = clockProvider.now();
        return outboxEventClaimRepository.claimAvailable(
                publisherIdentity,
                now,
                now.minus(properties.getOutboxLeaseDuration()),
                properties.getOutboxBatchSize()
        );
    }

    String publisherIdentity() {
        return publisherIdentity;
    }
}
