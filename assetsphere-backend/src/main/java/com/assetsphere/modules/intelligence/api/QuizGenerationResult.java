package com.assetsphere.modules.intelligence.api;

import java.util.List;

public record QuizGenerationResult(String title, List<Question> questions) {
    public record Question(String text, List<String> options, String correctAnswer,
                           String explanation, List<String> sourceIds) { }
}
