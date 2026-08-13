package com.assetsphere.modules.workspace.application;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "assetsphere.security")
class WorkspaceInvitationProperties {

    @Min(1)
    private long invitationExpirationSeconds = 604800;
}
