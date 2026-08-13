package com.assetsphere.modules.processing.text;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import com.assetsphere.modules.processing.api.MediaTranscriptionProvider;
import com.assetsphere.modules.common.exception.PayloadTooLargeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(90)
class VideoTextExtractor implements TextExtractor {
    private static final Set<String> SUPPORTED = Set.of("video/mp4", "video/webm");
    private final ObjectProvider<MediaTranscriptionProvider> providers;

    VideoTextExtractor(ObjectProvider<MediaTranscriptionProvider> providers) { this.providers = providers; }

    @Override public boolean supports(AssetProcessingInput input) { return SUPPORTED.contains(input.mimeType()); }

    @Override
    public ExtractionResult extract(AssetProcessingInput input, InputStream content) {
        MediaTranscriptionProvider provider = providers.getIfAvailable();
        if (provider == null) return ExtractionResult.notApplicable("TRANSCRIPTION_PROVIDER_DISABLED");
        if (input.fileSize() > provider.maxInputBytes()) throw new PayloadTooLargeException("Video exceeds the configured transcription size limit");
        try {
            return ExtractionResult.extracted(provider.transcribe(input, content.readAllBytes()), "MEDIA_TRANSCRIPTION");
        } catch (IOException exception) {
            throw new TextExtractionException("Video transcription input could not be read", exception);
        }
    }
}
