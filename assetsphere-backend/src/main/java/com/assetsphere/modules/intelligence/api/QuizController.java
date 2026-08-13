package com.assetsphere.modules.intelligence.api;

import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.web.ApiResponse;
import com.assetsphere.modules.intelligence.api.dto.request.GenerateQuizRequest;
import com.assetsphere.modules.intelligence.api.dto.response.QuizResponse;
import com.assetsphere.modules.intelligence.application.QuizGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@Tag(name = "Quiz")
class QuizController {
    private final QuizGenerationService quizzes;
    private final CurrentUserProvider currentUser;
    private final ClockProvider clock;

    @PostMapping("/quiz")
    @Operation(summary = "Generate a grounded workspace quiz")
    ApiResponse<QuizResponse> workspace(@PathVariable UUID workspaceId, @RequestBody GenerateQuizRequest request) {
        return ApiResponse.success(quizzes.forWorkspace(currentUser.requireCurrentUser().id(), workspaceId, request), clock);
    }

    @PostMapping("/assets/{assetId}/versions/{versionNumber}/quiz")
    @Operation(summary = "Generate a quiz from an exact asset version")
    ApiResponse<QuizResponse> asset(@PathVariable UUID workspaceId, @PathVariable UUID assetId,
                                    @PathVariable int versionNumber, @RequestBody GenerateQuizRequest request) {
        return ApiResponse.success(quizzes.forAsset(currentUser.requireCurrentUser().id(), workspaceId, assetId, versionNumber, request), clock);
    }
}
