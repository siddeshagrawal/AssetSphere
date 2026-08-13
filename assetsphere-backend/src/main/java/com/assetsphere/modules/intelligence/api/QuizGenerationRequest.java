package com.assetsphere.modules.intelligence.api;

import java.util.List;

public record QuizGenerationRequest(int questionCount, QuizDifficulty difficulty, String modelId, List<Source> sources) {
    public record Source(String id, String text) { }
}
