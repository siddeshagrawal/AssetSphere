package com.assetsphere.modules.asset.api;

import java.time.Duration;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "assetsphere.asset.upload")
public class AssetUploadProperties {
    private DataSize maxFileSize = DataSize.ofMegabytes(25);
    private Set<String> allowedMimeTypes = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/markdown",
            "text/x-markdown",
            "text/csv",
            "application/csv",
            "application/vnd.ms-excel",
            "application/json",
            "text/json",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "image/png",
            "image/jpeg",
            "image/webp",
            "video/mp4",
            "video/webm"
    );
    private boolean allowOtherTypes;
    private Duration idempotencyInProgressTtl = Duration.ofMinutes(15);
    private Duration idempotencyFailedTtl = Duration.ofMinutes(15);
    private Duration idempotencyCompletedTtl = Duration.ofHours(24);
    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class RateLimit {
        private int permits = 20;
        private Duration window = Duration.ofMinutes(1);
    }
}
