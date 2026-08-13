package com.assetsphere.modules.asset.api;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "assetsphere.asset.cache")
public class AssetCacheProperties {
    private Duration metadataTtl = Duration.ofMinutes(5);
}
