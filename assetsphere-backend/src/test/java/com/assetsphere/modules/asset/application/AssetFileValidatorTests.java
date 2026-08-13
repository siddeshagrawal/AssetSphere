package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.asset.api.AssetUploadProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.asset.domain.AssetType;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.common.exception.PayloadTooLargeException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class AssetFileValidatorTests {

    @Test
    void acceptsConfiguredSupportedTypes() {
        AssetFileValidator validator = validator(false, DataSize.ofBytes(10));

        assertThat(validator.validate(file("report.pdf", "application/pdf")).assetType()).isEqualTo(AssetType.PDF);
        assertThat(validator.validate(file("report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .assetType()).isEqualTo(AssetType.DOCX);
        assertThat(validator.validate(file("image.png", "image/png")).assetType()).isEqualTo(AssetType.IMAGE);
        assertThat(validator.validate(file("image.jpg", "image/jpeg")).assetType()).isEqualTo(AssetType.IMAGE);
        assertThat(validator.validate(file("image.webp", "image/webp")).assetType()).isEqualTo(AssetType.IMAGE);
    }

    @Test
    void acceptsSupportedDocumentFormatsAsOtherAssets() {
        AssetFileValidator validator = validator(false, DataSize.ofBytes(10));

        for (var file : java.util.List.of(
                file("notes.txt", "text/plain"), file("notes.md", "text/markdown"),
                file("data.csv", "text/csv"), file("data.json", "application/json"),
                file("sheet.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                file("slides.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
        )) {
            assertThat(validator.validate(file).assetType()).isEqualTo(AssetType.OTHER);
        }
    }

    @Test
    void infersSupportedMimeForGenericBrowserUploadsAndRejectsMismatchedExtensions() {
        AssetFileValidator validator = validator(false, DataSize.ofBytes(10));

        assertThat(validator.validate(file("notes.md", "application/octet-stream")).mimeType())
                .isEqualTo("text/markdown");
        assertThatThrownBy(() -> validator.validate(file("report.txt", "application/pdf")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Unsupported file type");
    }

    @Test
    void validatesVideoMimeAndExtensionTogether() {
        AssetFileValidator validator = validator(false, DataSize.ofBytes(10));

        assertThat(validator.validate(file("clip.mp4", "video/mp4")).mimeType()).isEqualTo("video/mp4");
        assertThat(validator.validate(file("clip.webm", "video/webm")).mimeType()).isEqualTo("video/webm");
        assertThatThrownBy(() -> validator.validate(file("clip.bin", "video/mp4")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Unsupported file type");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> validator(false, DataSize.ofBytes(10))
                .validate(new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsOversizedFile() {
        assertThatThrownBy(() -> validator(false, DataSize.ofBytes(1)).validate(file("report.pdf", "application/pdf")))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void rejectsUnsupportedAndOtherTypesByDefault() {
        assertThatThrownBy(() -> validator(false, DataSize.ofBytes(10))
                .validate(file("script.exe", "application/octet-stream")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Unsupported file type");
    }

    @Test
    void allowsOtherTypeWhenEnabled() {
        assertThat(validator(true, DataSize.ofBytes(10))
                .validate(file("data.bin", "application/octet-stream")).assetType()).isEqualTo(AssetType.OTHER);
    }

    @Test
    void rejectsTraversalPathContainingAndMissingFilenames() {
        AssetFileValidator validator = validator(false, DataSize.ofBytes(10));

        assertThatThrownBy(() -> validator.validate(file("../report.pdf", "application/pdf")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> validator.validate(file("folder/report.pdf", "application/pdf")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("file", null, "application/pdf", new byte[] {1})))
                .isInstanceOf(InvalidRequestException.class);
    }

    private AssetFileValidator validator(boolean allowOtherTypes, DataSize maxFileSize) {
        AssetUploadProperties properties = new AssetUploadProperties();
        properties.setAllowOtherTypes(allowOtherTypes);
        properties.setMaxFileSize(maxFileSize);
        return new AssetFileValidator(properties);
    }

    private MockMultipartFile file(String filename, String mimeType) {
        return new MockMultipartFile("file", filename, mimeType, new byte[] {1, 2});
    }
}
