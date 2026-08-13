package com.assetsphere.modules.processing.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.processing.api.ProcessingProperties;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.assetsphere.modules.processing.api.ImageOcrProvider;
import com.assetsphere.modules.processing.api.MediaTranscriptionProvider;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.util.unit.DataSize;

class TextExtractionServiceTests {

    @Test
    void truncatesRetainedTextAndRecordsIt() {
        ProcessingProperties properties = new ProcessingProperties();
        properties.setMaxRetainedTextCharacters(5);
        TextExtractionService service = new TextExtractionService(List.of(new FixedExtractor("one   two\n three")), properties);

        ExtractionResult result = service.extract(input(10), new ByteArrayInputStream(new byte[0]));

        assertThat(result.text()).isEqualTo("one t");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void rejectsFilesOverConfiguredProcessingLimit() {
        ProcessingProperties properties = new ProcessingProperties();
        properties.setMaxProcessingFileSize(DataSize.ofBytes(1));
        TextExtractionService service = new TextExtractionService(List.of(new FixedExtractor("text")), properties);

        assertThatThrownBy(() -> service.extract(input(2), new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void representsImagesAsDisabledWhenNoOcrProviderExists() {
        ObjectProvider<ImageOcrProvider> providers = mock(ObjectProvider.class);
        ImageTextExtractor extractor = new ImageTextExtractor(providers);
        ExtractionResult result = extractor.extract(input("image/png", 1), new ByteArrayInputStream(new byte[0]));

        assertThat(result.status()).isEqualTo(ExtractionStatus.NOT_APPLICABLE);
        assertThat(result.extractorType()).isEqualTo("OCR_PROVIDER_DISABLED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void routesImageBytesThroughConfiguredOcrProvider() {
        ObjectProvider<ImageOcrProvider> providers = mock(ObjectProvider.class);
        ImageOcrProvider provider = mock(ImageOcrProvider.class);
        when(providers.getIfAvailable()).thenReturn(provider);
        when(provider.maxInputBytes()).thenReturn(10L);
        when(provider.extractText(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("recognized text");
        ExtractionResult result = new ImageTextExtractor(providers).extract(
                input("image/png", 3), new ByteArrayInputStream(new byte[] {1, 2, 3}));
        assertThat(result.text()).isEqualTo("recognized text");
        assertThat(result.extractorType()).isEqualTo("IMAGE_OCR");
    }

    @Test
    @SuppressWarnings("unchecked")
    void routesVideoBytesThroughConfiguredTranscriptionProvider() {
        ObjectProvider<MediaTranscriptionProvider> providers = mock(ObjectProvider.class);
        MediaTranscriptionProvider provider = mock(MediaTranscriptionProvider.class);
        when(providers.getIfAvailable()).thenReturn(provider);
        when(provider.maxInputBytes()).thenReturn(10L);
        when(provider.transcribe(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("transcribed text");
        ExtractionResult result = new VideoTextExtractor(providers).extract(
                input("video/mp4", 3), new ByteArrayInputStream(new byte[] {1, 2, 3}));
        assertThat(result.text()).isEqualTo("transcribed text");
        assertThat(result.extractorType()).isEqualTo("MEDIA_TRANSCRIPTION");
    }

    @Test
    void representsUnsupportedTypesAsMetadataOnly() {
        UnsupportedTextExtractor extractor = new UnsupportedTextExtractor();
        ExtractionResult result = extractor.extract(input("application/octet-stream", 1), new ByteArrayInputStream(new byte[0]));

        assertThat(result.status()).isEqualTo(ExtractionStatus.UNSUPPORTED);
        assertThat(result.text()).isBlank();
    }

    private AssetProcessingInput input(long size) {
        return input("application/pdf", size);
    }

    private AssetProcessingInput input(String mimeType, long size) {
        return new AssetProcessingInput(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "object", "name", null,
                "source", mimeType, size);
    }

    private record FixedExtractor(String value) implements TextExtractor {
        @Override
        public boolean supports(AssetProcessingInput input) {
            return true;
        }

        @Override
        public ExtractionResult extract(AssetProcessingInput input, java.io.InputStream content) {
            return ExtractionResult.extracted(value, "TEST");
        }
    }
}
