package com.assetsphere.modules.intelligence.api.dto.response;

import java.util.List;

public record QuizResponse(String title, List<Question> questions) {
    public record Question(String text, String type, List<String> options, String correctAnswer,
                           String explanation, List<String> sourceIds) { }
}
