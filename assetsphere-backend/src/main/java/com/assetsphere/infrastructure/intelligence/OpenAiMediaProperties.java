package com.assetsphere.infrastructure.intelligence;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Getter @Setter @Component
@ConfigurationProperties(prefix = "assetsphere.ai.media")
class OpenAiMediaProperties {
    private boolean ocrEnabled;
    private boolean transcriptionEnabled;
    private String apiKey;
    private String baseUrl = "https://api.openai.com";
    private String visionModel = "gpt-4o-mini";
    private String transcriptionModel = "gpt-4o-mini-transcribe";
    private DataSize maxImageSize = DataSize.ofMegabytes(10);
    private DataSize maxVideoSize = DataSize.ofMegabytes(25);

    void requireConfigured() {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("OpenAI API key is not configured");
    }
}
