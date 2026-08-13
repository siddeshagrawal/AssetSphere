package com.assetsphere.modules.search.application;

import com.assetsphere.modules.search.api.SearchIndexCommand;
import com.assetsphere.modules.search.api.SearchIndexFacade;
import com.assetsphere.modules.search.persistence.AssetSearchDocumentRepository;
import com.assetsphere.modules.asset.api.AssetMetadataUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class SearchIndexApplicationService implements SearchIndexFacade {

    private final AssetSearchDocumentRepository assetSearchDocumentRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void index(SearchIndexCommand command) {
        assetSearchDocumentRepository.upsert(command);
    }

    @org.springframework.context.event.EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateMetadata(AssetMetadataUpdatedEvent event) {
        assetSearchDocumentRepository.updateMetadata(
                event.workspaceId(), event.assetId(), event.displayName(), event.description()
        );
    }
}
