package com.assetsphere.modules.processing.outbox.application;

import com.assetsphere.modules.processing.api.OutboxMessagePublisher;
import com.assetsphere.modules.processing.outbox.persistence.OutboxClaim;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class OutboxPublisher {

    private final OutboxClaimingService claimingService;
    private final OutboxPublishingStateService stateService;
    private final OutboxMessagePublisher messagePublisher;

    @Scheduled(fixedDelayString = "${assetsphere.processing.outbox-poll-interval:PT1S}")
    void publishAvailable() {
        List<OutboxClaim> claims = claimingService.claimAvailable();
        for (OutboxClaim claim : claims) {
            publish(claim);
        }
    }

    private void publish(OutboxClaim claim) {
        try {
            messagePublisher.publish(claim.id(), claim.aggregateId(), claim.topic(), claim.payload());
            stateService.markPublished(claim.id(), claimingService.publisherIdentity());
        } catch (RuntimeException exception) {
            stateService.recordFailure(
                    claim.id(), claimingService.publisherIdentity(), claim.retryCount(), safeError(exception)
            );
            log.warn("Outbox publish failed eventId={} type={}", claim.id(), exception.getClass().getSimpleName());
        }
    }

    private String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }
}
