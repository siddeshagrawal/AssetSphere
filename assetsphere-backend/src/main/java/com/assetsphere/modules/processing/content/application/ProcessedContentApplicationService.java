package com.assetsphere.modules.processing.content.application;

import com.assetsphere.modules.processing.api.ProcessedContent;
import com.assetsphere.modules.processing.api.ProcessedContentFacade;
import com.assetsphere.modules.processing.content.persistence.AssetTextContentRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ProcessedContentApplicationService implements ProcessedContentFacade {

    private final AssetTextContentRepository contents;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedContent> findByAssetVersionId(UUID assetVersionId) {
        return contents.findByAssetVersionId(assetVersionId).map(content -> new ProcessedContent(
                content.getWorkspaceId(), content.getAssetId(), content.getAssetVersionId(), content.getExtractedText(),
                content.getExtractionStatus().name(), content.isTruncated()
        ));
    }
}
