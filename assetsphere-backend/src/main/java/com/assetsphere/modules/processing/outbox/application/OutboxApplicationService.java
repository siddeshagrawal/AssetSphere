package com.assetsphere.modules.processing.outbox.application;

import com.assetsphere.modules.asset.api.AssetUploadedEvent;
import com.assetsphere.modules.processing.api.AssetReadyForIntelligenceEvent;
import com.assetsphere.modules.processing.api.AssetReadyForSemanticIndexEvent;
import com.assetsphere.modules.processing.outbox.domain.OutboxEvent;
import com.assetsphere.modules.processing.outbox.persistence.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class OutboxApplicationService {

    private final OutboxEventRepository outboxEvents;
    private final ObjectMapper objectMapper;

    @org.springframework.context.event.EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void create(AssetUploadedEvent event) {
        outboxEvents.save(OutboxEvent.createPending(
                "ASSET",
                event.assetId(),
                "asset.uploaded.v1",
                event.eventVersion(),
                "assets.uploaded.v1",
                write(event)
        ));
    }

    @org.springframework.context.event.EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void create(AssetReadyForIntelligenceEvent event) {
        outboxEvents.save(OutboxEvent.createPending(
                "ASSET",
                event.assetId(),
                AssetReadyForIntelligenceEvent.EVENT_TYPE,
                event.eventVersion(),
                AssetReadyForIntelligenceEvent.TOPIC,
                write(event)
        ));
    }

    @org.springframework.context.event.EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void create(AssetReadyForSemanticIndexEvent event) {
        outboxEvents.save(OutboxEvent.createPending("ASSET", event.assetId(), AssetReadyForSemanticIndexEvent.EVENT_TYPE,
                event.eventVersion(), AssetReadyForSemanticIndexEvent.TOPIC, write(event)));
    }

    private String write(AssetUploadedEvent event) {
        return write((Object) event);
    }

    private String write(AssetReadyForIntelligenceEvent event) {
        return write((Object) event);
    }

    private String write(AssetReadyForSemanticIndexEvent event) { return write((Object) event); }

    private String write(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize asset uploaded event", exception);
        }
    }
}
