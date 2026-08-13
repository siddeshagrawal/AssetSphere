package com.assetsphere.modules.intelligence.api.dto.response;

import java.util.List;

public record AssetEvolutionResponse(
        int fromVersion,
        int toVersion,
        String executiveSummary,
        List<String> keyChanges,
        List<String> additions,
        List<String> removals,
        List<String> importantChanges
) { }
