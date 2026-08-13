package com.assetsphere.modules.intelligence.api;

public interface QuizGenerationModel {
    QuizGenerationResult generate(QuizGenerationRequest request);
}
