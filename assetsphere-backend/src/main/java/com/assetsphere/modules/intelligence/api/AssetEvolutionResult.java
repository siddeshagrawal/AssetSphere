package com.assetsphere.modules.intelligence.api;

import java.util.List;

public record AssetEvolutionResult(
        String executiveSummary,
        List<String> keyChanges,
        List<String> additions,
        List<String> removals,
        List<String> importantChanges
) { }
