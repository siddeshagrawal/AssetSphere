package com.assetsphere.modules.search.api;

import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.web.ApiResponse;
import com.assetsphere.modules.common.web.PageResponse;
import com.assetsphere.modules.search.application.SearchApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/search")
@Tag(name = "Search")
@SecurityRequirement(name = "bearerAuth")
class SearchController {

    private final SearchApplicationService searchService;
    private final CurrentUserProvider currentUserProvider;
    private final ClockProvider clockProvider;

    @GetMapping
    @Operation(summary = "Search workspace asset metadata and extracted text")
    ApiResponse<PageResponse<AssetSearchResult>> search(
            @PathVariable UUID workspaceId,
            @RequestParam String q,
            @RequestParam(required = false) String mode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        return ApiResponse.success(searchService.search(
                currentUserProvider.requireCurrentUser().id(), workspaceId, q, page, size, SearchMode.from(mode)), clockProvider);
    }
}
