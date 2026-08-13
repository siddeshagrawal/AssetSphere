package com.assetsphere.modules.asset.api;

import com.assetsphere.modules.asset.api.dto.response.AssetResponse;
import com.assetsphere.modules.asset.api.dto.response.AssetVersionResponse;
import com.assetsphere.modules.asset.application.AssetQueryService;
import com.assetsphere.modules.asset.application.AssetUploadService;
import com.assetsphere.modules.asset.application.AssetMetadataApplicationService;
import com.assetsphere.modules.asset.api.dto.request.UpdateAssetMetadataRequest;
import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.web.ApiResponse;
import com.assetsphere.modules.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/assets")
@Tag(name = "Assets")
@SecurityRequirement(name = "bearerAuth")
class AssetController {

    private final AssetUploadService assetUploadService;
    private final AssetQueryService assetQueryService;
    private final AssetMetadataApplicationService assetMetadataApplicationService;
    private final CurrentUserProvider currentUserProvider;
    private final ClockProvider clockProvider;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an Asset")
    ResponseEntity<ApiResponse<AssetResponse>> upload(
            @PathVariable UUID workspaceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String description
    ) {
        AssetUploadService.UploadResult result = assetUploadService.upload(
                workspaceId, idempotencyKey, file, displayName, description
        );
        URI location = URI.create("/api/v1/workspaces/%s/assets/%s"
                .formatted(workspaceId, result.response().assetId()));
        return ResponseEntity.created(location)
                .header("X-Idempotent-Replay", Boolean.toString(result.replayed()))
                .body(ApiResponse.success(result.response(), clockProvider));
    }

    @PostMapping(path = "/{assetId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a new Asset version")
    ResponseEntity<ApiResponse<AssetResponse>> uploadVersion(
            @PathVariable UUID workspaceId,
            @PathVariable UUID assetId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestPart("file") MultipartFile file
    ) {
        AssetUploadService.UploadResult result = assetUploadService.uploadVersion(
                workspaceId, assetId, idempotencyKey, file
        );
        URI location = URI.create("/api/v1/workspaces/%s/assets/%s/versions/%d"
                .formatted(workspaceId, assetId, result.response().versionNumber()));
        return ResponseEntity.created(location)
                .header("X-Idempotent-Replay", Boolean.toString(result.replayed()))
                .body(ApiResponse.success(result.response(), clockProvider));
    }

    @GetMapping("/{assetId}/versions")
    @Operation(summary = "List Asset version history")
    ApiResponse<List<AssetVersionResponse>> listVersions(
            @PathVariable UUID workspaceId, @PathVariable UUID assetId
    ) {
        return ApiResponse.success(assetQueryService.listVersions(
                currentUserProvider.requireCurrentUser().id(), workspaceId, assetId
        ), clockProvider);
    }

    @GetMapping("/{assetId}/versions/{versionNumber}")
    @Operation(summary = "Get Asset version metadata")
    ApiResponse<AssetVersionResponse> getVersion(
            @PathVariable UUID workspaceId, @PathVariable UUID assetId,
            @PathVariable @Min(1) int versionNumber
    ) {
        return ApiResponse.success(assetQueryService.getVersion(
                currentUserProvider.requireCurrentUser().id(), workspaceId, assetId, versionNumber
        ), clockProvider);
    }

    @GetMapping("/{assetId}/versions/{versionNumber}/download")
    @Operation(summary = "Download an Asset version")
    ResponseEntity<InputStreamResource> downloadVersion(
            @PathVariable UUID workspaceId, @PathVariable UUID assetId,
            @PathVariable @Min(1) int versionNumber
    ) {
        AssetQueryService.VersionDownload download = assetQueryService.downloadVersion(
                currentUserProvider.requireCurrentUser().id(), workspaceId, assetId, versionNumber
        );
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .contentLength(download.fileSize())
                .body(new InputStreamResource(download.content()));
    }

    @GetMapping("/{assetId}")
    @Operation(summary = "Get Asset metadata")
    ApiResponse<AssetResponse> get(@PathVariable UUID workspaceId, @PathVariable UUID assetId) {
        return ApiResponse.success(assetQueryService.get(currentUserProvider.requireCurrentUser().id(), workspaceId, assetId), clockProvider);
    }

    @PatchMapping("/{assetId}")
    @Operation(summary = "Update Asset metadata")
    ApiResponse<AssetResponse> update(
            @PathVariable UUID workspaceId, @PathVariable UUID assetId,
            @Valid @RequestBody UpdateAssetMetadataRequest request
    ) {
        return ApiResponse.success(assetMetadataApplicationService.update(
                currentUserProvider.requireCurrentUser().id(), workspaceId, assetId, request
        ), clockProvider);
    }

    @GetMapping
    @Operation(summary = "List Asset metadata")
    ApiResponse<PageResponse<AssetResponse>> list(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        return ApiResponse.success(assetQueryService.list(currentUserProvider.requireCurrentUser().id(), workspaceId, page, size), clockProvider);
    }
}
