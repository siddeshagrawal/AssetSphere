package com.assetsphere.modules.auth.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "assetsphere.jwt")
class JwtProperties {

    @NotBlank
    @Size(min = 32)
    private String secret;

    @Min(1)
    private long accessTokenExpirationSeconds = 900;
}
