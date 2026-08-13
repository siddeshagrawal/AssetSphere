package com.assetsphere.modules.intelligence.api.dto.request;

import com.assetsphere.modules.intelligence.api.QuizDifficulty;

public record GenerateQuizRequest(Integer questionCount, QuizDifficulty difficulty, String topic, String modelId) { }
