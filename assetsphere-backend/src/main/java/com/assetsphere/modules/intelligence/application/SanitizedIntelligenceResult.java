package com.assetsphere.modules.intelligence.application;

import java.util.List;

record SanitizedIntelligenceResult(String summary, List<String> keyPoints, List<String> tags) {
}
