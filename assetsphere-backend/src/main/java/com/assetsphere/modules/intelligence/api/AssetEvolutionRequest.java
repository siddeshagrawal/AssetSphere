package com.assetsphere.modules.intelligence.api;

public record AssetEvolutionRequest(
        String modelId,
        int fromVersion, String fromFilename, String fromMimeType, String fromContent,
        int toVersion, String toFilename, String toMimeType, String toContent
) { }
