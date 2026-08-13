package com.assetsphere.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "assetsphere")
public class ApplicationProperties {
    private Storage storage = new Storage();

    @Getter
    @Setter
    public static class Storage {
        private Minio minio = new Minio();
    }

    @Getter
    @Setter
    public static class Minio {
        private boolean enabled;
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private boolean autoCreateBucket;
    }

}
