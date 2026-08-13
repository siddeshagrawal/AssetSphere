package com.assetsphere.infrastructure.intelligence;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.exception.PayloadTooLargeException;
import com.assetsphere.modules.processing.api.MediaTranscriptionProvider;
import com.assetsphere.modules.processing.api.MediaProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(prefix = "assetsphere.ai.media", name = "transcription-enabled", havingValue = "true")
class OpenAiMediaTranscriptionProvider implements MediaTranscriptionProvider {
    private final OpenAiMediaProperties properties;
    private final BillingEntitlementFacade billing;
    private final RestClient client;

    OpenAiMediaTranscriptionProvider(OpenAiMediaProperties properties, BillingEntitlementFacade billing, RestClient.Builder builder) {
        this.properties = properties; this.billing = billing; this.client = builder.clone().baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public long maxInputBytes() { return properties.getMaxVideoSize().toBytes(); }

    @Override
    public String transcribe(AssetProcessingInput input, byte[] media) {
        if (!billing.entitlements(input.workspaceId()).videoTranscriptionEnabled()) throw new BusinessRuleViolationException("Video transcription requires a PRO or ENTERPRISE workspace plan");
        if (media.length > properties.getMaxVideoSize().toBytes()) throw new PayloadTooLargeException("Video exceeds the configured transcription size limit");
        try { properties.requireConfigured(); } catch (IllegalStateException exception) { throw MediaProviderException.nonRetryable("Transcription provider is not configured", exception); }
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("model", properties.getTranscriptionModel());
        form.add("file", new ByteArrayResource(media) { @Override public String getFilename() { return "asset" + ("video/webm".equals(input.mimeType()) ? ".webm" : ".mp4"); } });
        try {
            JsonNode response = client.post().uri("/v1/audio/transcriptions").headers(headers -> headers.setBearerAuth(properties.getApiKey()))
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(JsonNode.class);
            String text = response == null ? "" : response.path("text").asText();
            if (text.isBlank()) throw MediaProviderException.nonRetryable("Transcription provider returned no text", null);
            return text;
        } catch (MediaProviderException exception) { throw exception; }
        catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429 || exception.getStatusCode().is5xxServerError()) throw MediaProviderException.retryable("Transcription provider is temporarily unavailable", null);
            throw MediaProviderException.nonRetryable("Transcription provider rejected the media", null);
        } catch (RestClientException exception) { throw MediaProviderException.retryable("Transcription provider is temporarily unavailable", exception); }
    }
}
