package com.assetsphere.modules.intelligence.api;

import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.web.ApiResponse;
import com.assetsphere.modules.intelligence.api.dto.response.AssetIntelligenceResponse;
import com.assetsphere.modules.intelligence.application.AssetIntelligenceQueryService;
import com.assetsphere.modules.intelligence.application.AssetIntelligenceGenerationService;
import com.assetsphere.modules.intelligence.application.AssetEvolutionApplicationService;
import com.assetsphere.modules.intelligence.api.dto.request.CompareAssetVersionsRequest;
import com.assetsphere.modules.intelligence.api.dto.request.GenerateIntelligenceRequest;
import com.assetsphere.modules.intelligence.api.dto.response.AssetEvolutionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/assets/{assetId}")
@Tag(name = "Intelligence")
@SecurityRequirement(name = "bearerAuth")
class AssetIntelligenceController {

    private final AssetIntelligenceQueryService intelligenceQueryService;
    private final AssetIntelligenceGenerationService intelligenceGenerationService;
    private final AssetEvolutionApplicationService assetEvolutionApplicationService;
    private final CurrentUserProvider currentUserProvider;
    private final ClockProvider clock;

    @GetMapping("/intelligence")
    @Operation(summary = "Get AI-generated Asset Intelligence")
    ApiResponse<AssetIntelligenceResponse> get(@PathVariable UUID workspaceId, @PathVariable UUID assetId) {
        return ApiResponse.success(
                intelligenceQueryService.get(currentUserProvider.requireCurrentUser().id(), workspaceId, assetId), clock
        );
    }

    @GetMapping("/versions/{versionNumber}/intelligence")
    @Operation(summary = "Get AI-generated intelligence for an Asset version")
    ApiResponse<AssetIntelligenceResponse> getVersion(
            @PathVariable UUID workspaceId, @PathVariable UUID assetId, @PathVariable @Min(1) int versionNumber
    ) {
        return ApiResponse.success(intelligenceQueryService.getVersion(
                currentUserProvider.requireCurrentUser().id(), workspaceId, assetId, versionNumber
        ), clock);
    }

    @PostMapping("/versions/{versionNumber}/intelligence/generate")
    @Operation(summary = "Generate AI intelligence for an Asset version")
    ApiResponse<AssetIntelligenceResponse> generate(
            @PathVariable UUID workspaceId, @PathVariable UUID assetId, @PathVariable @Min(1) int versionNumber,
            @Valid @RequestBody(required = false) GenerateIntelligenceRequest request
    ) {
        return ApiResponse.success(intelligenceGenerationService.generate(
                currentUserProvider.requireCurrentUser().id(), workspaceId, assetId, versionNumber,
                request == null ? null : request.modelId()
        ), clock);
    }

    @PostMapping("/compare")
    @Operation(summary = "Compare two Asset versions")
    ApiResponse<AssetEvolutionResponse> compare(
            @PathVariable UUID workspaceId, @PathVariable UUID assetId,
            @Valid @RequestBody CompareAssetVersionsRequest request
    ) {
        return ApiResponse.success(assetEvolutionApplicationService.compare(
                currentUserProvider.requireCurrentUser().id(), workspaceId, assetId, request
        ), clock);
    }
}
