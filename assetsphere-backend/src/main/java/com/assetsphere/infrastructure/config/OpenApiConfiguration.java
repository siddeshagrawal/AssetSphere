package com.assetsphere.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI assetSphereOpenApi() {
        return new OpenAPI().info(new Info().title("AssetSphere API").version("v1").description("AssetSphere platform API"));
    }
}
