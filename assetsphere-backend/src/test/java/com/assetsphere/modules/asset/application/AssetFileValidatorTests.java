package com.assetsphere.modules.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.asset.domain.AssetType;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AssetFileValidatorTests {

    private final AssetFileValidator validator = new AssetFileValidator();

    @Test
    void acceptsSupportedPdf() {
        var file = new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[] {1});

        var result = validator.validate(file);

        assertThat(result.assetType()).isEqualTo(AssetType.PDF);
    }

    @Test
    void rejectsUnsafeFilename() {
        var file = new MockMultipartFile("file", "../report.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Filename is unsafe");
    }

    @Test
    void rejectsUnsupportedMimeType() {
        var file = new MockMultipartFile("file", "script.exe", "application/octet-stream", new byte[] {1});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Unsupported file type");
    }
}
