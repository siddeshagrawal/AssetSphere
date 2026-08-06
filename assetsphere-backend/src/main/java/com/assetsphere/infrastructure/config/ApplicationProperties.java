package com.assetsphere.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "assetsphere")
public class ApplicationProperties {
    private Jwt jwt = new Jwt();
    private Security security = new Security();
    private Storage storage = new Storage();
    private Ai ai = new Ai();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long accessTokenExpirationSeconds = 900;
        private long refreshTokenExpirationSeconds = 604800;
    }
    @Getter @Setter
    public static class Security {
        private int loginMaxFailures = 5;
        private long lockDurationSeconds = 900;
        private long invitationExpirationSeconds = 604800;
    }

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
    }

    @Getter
    @Setter
    public static class Ai {
        private boolean enabled;
    }
}
