package com.assetsphere.modules.intelligence.api;

import java.util.List;

public record DocumentIntelligenceResult(String summary, List<String> keyPoints, List<String> tags) {
}
