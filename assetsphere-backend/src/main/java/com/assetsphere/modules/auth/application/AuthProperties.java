package com.assetsphere.modules.auth.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "assetsphere.auth")
public class AuthProperties {

    @NotNull
    private Duration refreshTokenTtl = Duration.ofDays(7);

    @Min(1)
    private int loginMaxFailures = 5;

    @NotNull
    private Duration lockDuration = Duration.ofMinutes(15);
}
