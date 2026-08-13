package com.assetsphere.infrastructure.config;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.models.OpenAPI;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigurationTests {

    @Test
    void declaresBearerAuthenticationForProtectedOperationsOnly() throws ClassNotFoundException, NoSuchMethodException {
        OpenAPI openApi = new OpenApiConfiguration().assetSphereOpenApi();

        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(openApi.getComponents().getSecuritySchemes().get("bearerAuth").getType())
                .isEqualTo(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP);
        assertThat(openApi.getComponents().getSecuritySchemes().get("bearerAuth").getScheme()).isEqualTo("bearer");
        Class<?> workspaceController = Class.forName("com.assetsphere.modules.workspace.api.WorkspaceController");
        Class<?> authenticationController = Class.forName("com.assetsphere.modules.auth.api.AuthenticationController");
        assertThat(workspaceController.getAnnotation(SecurityRequirement.class).name()).isEqualTo("bearerAuth");

        Method register = authenticationController.getDeclaredMethod(
                "register", com.assetsphere.modules.auth.api.dto.request.RegisterRequest.class
        );
        Method login = authenticationController.getDeclaredMethod(
                "login", com.assetsphere.modules.auth.api.dto.request.LoginRequest.class, HttpServletRequest.class
        );

        assertThat(register.getAnnotation(SecurityRequirement.class)).isNull();
        assertThat(login.getAnnotation(SecurityRequirement.class)).isNull();
    }
}
