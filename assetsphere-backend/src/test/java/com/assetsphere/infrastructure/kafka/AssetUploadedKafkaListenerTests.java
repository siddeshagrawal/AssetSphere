package com.assetsphere.infrastructure.kafka;

import static org.mockito.Mockito.verify;

import com.assetsphere.modules.asset.api.AssetUploadedEvent;
import com.assetsphere.modules.asset.domain.AssetProcessingStatus;
import com.assetsphere.modules.processing.api.AssetEventProcessingFacade;
import com.assetsphere.modules.processing.api.ProcessingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AssetUploadedKafkaListenerTests {
    @Test void validDltEventDelegatesTerminalFailureForTheRealAssetVersion() throws Exception {
        AssetEventProcessingFacade processor = Mockito.mock(AssetEventProcessingFacade.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AssetUploadedKafkaListener listener = new AssetUploadedKafkaListener(objectMapper, processor, new ProcessingProperties());
        AssetUploadedEvent event = new AssetUploadedEvent(UUID.randomUUID(),1,Instant.EPOCH,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"file.pdf","application/pdf",1,"a".repeat(64),"object", AssetProcessingStatus.UPLOADED);
        listener.consumeDeadLetter(objectMapper.writeValueAsString(event), "assets.uploaded.v1.DLT");
        verify(processor).markFailed(event);
    }
}
