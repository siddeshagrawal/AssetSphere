package com.assetsphere.modules.intelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.intelligence.api.IntelligenceProvider;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetIntelligenceTests {

    @Test
    void transitionsFromPendingThroughProcessingToReady() {
        AssetIntelligence intelligence = pending();
        intelligence.start(IntelligenceProvider.OPENAI, "test-model", 42, false, Instant.EPOCH);
        intelligence.complete("Summary", "[\"Point\"]", "[\"tag\"]", Instant.EPOCH.plusSeconds(1));
        assertThat(intelligence.getStatus()).isEqualTo(IntelligenceStatus.READY);
        assertThat(intelligence.isTerminal()).isTrue();
    }

    @Test
    void rejectsInvalidCompletionTransition() {
        assertThatThrownBy(() -> pending().complete("Summary", "[]", "[]", Instant.EPOCH))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void boundsFailureMessagesAndMarksTerminalFailure() {
        AssetIntelligence intelligence = pending();
        intelligence.start(IntelligenceProvider.OPENAI, "test-model", 42, false, Instant.EPOCH);
        intelligence.fail("PROVIDER_FAILURE", "x".repeat(1_100), Instant.EPOCH.plusSeconds(1));
        assertThat(intelligence.getStatus()).isEqualTo(IntelligenceStatus.FAILED);
        assertThat(intelligence.getFailureMessage()).hasSize(1_000);
    }

    @Test
    void marksNoTextAsNotApplicableWithoutChangingAssetState() {
        AssetIntelligence intelligence = pending();
        intelligence.markNotApplicable(Instant.EPOCH);
        assertThat(intelligence.getStatus()).isEqualTo(IntelligenceStatus.NOT_APPLICABLE);
    }

    private AssetIntelligence pending() {
        return AssetIntelligence.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}
