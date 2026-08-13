package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.asset.api.dto.response.AssetResponse;
import com.assetsphere.modules.asset.api.AssetUploadRateLimiter;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.asset.persistence.AssetRepository;
import com.assetsphere.modules.common.security.CurrentUser;
import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.storage.api.StorageFacade;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetUploadService {

    private final CurrentUserProvider currentUserProvider;
    private final WorkspaceAccessFacade workspaceAccessFacade;
    private final AssetUploadRateLimiter assetUploadRateLimiter;
    private final AssetFileValidator assetFileValidator;
    private final AssetChecksum assetChecksum;
    private final UploadFingerprint uploadFingerprint;
    private final AssetIdempotencyService assetIdempotencyService;
    private final StorageFacade storageFacade;
    private final AssetUploadTransaction assetUploadTransaction;
    private final AssetRepository assetRepository;
    private final BillingEntitlementFacade billing;

    public UploadResult upload(UUID workspaceId, String idempotencyKey, MultipartFile file,
                               String displayName, String description) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        workspaceAccessFacade.requireActiveMembership(workspaceId, currentUser.id());
        assetUploadRateLimiter.check(workspaceId, currentUser.id());
        AssetFileValidator.ValidatedFile validatedFile = assetFileValidator.validate(file);
        String normalizedDisplayName = normalizeDisplayName(displayName, validatedFile.filename());
        String normalizedDescription = normalizeDescription(description);
        String fileChecksum = calculateChecksum(file);
        String requestFingerprint = uploadFingerprint.create(
                currentUser.id(),
                workspaceId,
                validatedFile.filename(),
                validatedFile.mimeType(),
                validatedFile.size(),
                fileChecksum,
                normalizedDisplayName,
                normalizedDescription
        );
        AssetIdempotencyService.Reservation reservation = assetIdempotencyService.reserve(
                currentUser.id(), workspaceId, idempotencyKey, requestFingerprint
        );
        if (reservation.isReplay()) {
            return UploadResult.replay(reservation.replayResponse());
        }
        try {
            billing.requireAssetUpload(workspaceId, validatedFile.size());
        } catch (RuntimeException exception) {
            compensate(null, reservation.recordId());
            throw exception;
        }

        StorageFacade.PreparedStorageObject preparedStorageObject = null;
        try (InputStream content = file.getInputStream()) {
            preparedStorageObject = storageFacade.prepare(new StorageFacade.PrepareStorageObjectCommand(
                    workspaceId,
                    fileChecksum,
                    validatedFile.mimeType(),
                    validatedFile.size(),
                    content
            ));
            AssetResponse response = assetUploadTransaction.persist(new AssetUploadTransaction.CreateAssetUploadCommand(
                    currentUser.id(),
                    workspaceId,
                    validatedFile.filename(),
                    normalizedDisplayName,
                    normalizedDescription,
                    validatedFile.mimeType(),
                    validatedFile.size(),
                    fileChecksum,
                    validatedFile.assetType(),
                    reservation.recordId(),
                    preparedStorageObject
            ));
            return UploadResult.created(response);
        } catch (IOException exception) {
            assetIdempotencyService.markFailed(reservation.recordId());
            throw new InvalidRequestException("Unable to read uploaded file");
        } catch (RuntimeException exception) {
            compensate(preparedStorageObject, reservation.recordId());
            throw exception;
        }
    }

    public UploadResult uploadVersion(UUID workspaceId, UUID assetId, String idempotencyKey, MultipartFile file) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        workspaceAccessFacade.requireActiveMembership(workspaceId, currentUser.id());
        assetUploadRateLimiter.check(workspaceId, currentUser.id());
        if (!assetRepository.existsByIdAndWorkspaceId(assetId, workspaceId)) {
            throw new ResourceNotFoundException("Asset not found");
        }
        AssetFileValidator.ValidatedFile validatedFile = assetFileValidator.validate(file);
        String fileChecksum = calculateChecksum(file);
        String requestFingerprint = uploadFingerprint.createVersion(
                currentUser.id(), workspaceId, assetId, validatedFile.filename(), validatedFile.mimeType(),
                validatedFile.size(), fileChecksum
        );
        AssetIdempotencyService.Reservation reservation = assetIdempotencyService.reserveVersion(
                currentUser.id(), workspaceId, idempotencyKey, requestFingerprint
        );
        if (reservation.isReplay()) {
            return UploadResult.replay(reservation.replayResponse());
        }
        try {
            billing.requireStorage(workspaceId, validatedFile.size());
        } catch (RuntimeException exception) {
            compensate(null, reservation.recordId());
            throw exception;
        }

        StorageFacade.PreparedStorageObject preparedStorageObject = null;
        try (InputStream content = file.getInputStream()) {
            preparedStorageObject = storageFacade.prepare(new StorageFacade.PrepareStorageObjectCommand(
                    workspaceId, fileChecksum, validatedFile.mimeType(), validatedFile.size(), content
            ));
            AssetResponse response = assetUploadTransaction.persistVersion(
                    new AssetUploadTransaction.CreateAssetVersionCommand(
                            currentUser.id(), workspaceId, assetId, validatedFile.filename(),
                            validatedFile.mimeType(), validatedFile.size(), fileChecksum, validatedFile.assetType(),
                            reservation.recordId(), preparedStorageObject
                    )
            );
            return UploadResult.created(response);
        } catch (IOException exception) {
            assetIdempotencyService.markFailed(reservation.recordId());
            throw new InvalidRequestException("Unable to read uploaded file");
        } catch (RuntimeException exception) {
            compensate(preparedStorageObject, reservation.recordId());
            throw exception;
        }
    }

    private String calculateChecksum(MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return assetChecksum.sha256(content);
        } catch (IOException exception) {
            throw new InvalidRequestException("Unable to read uploaded file");
        }
    }

    private void compensate(StorageFacade.PreparedStorageObject preparedStorageObject, UUID idempotencyRecordId) {
        try {
            if (preparedStorageObject != null) {
                storageFacade.compensate(preparedStorageObject);
            }
        } catch (RuntimeException compensationFailure) {
            log.warn("Asset upload compensation could not complete", compensationFailure);
        } finally {
            assetIdempotencyService.markFailed(idempotencyRecordId);
        }
    }

    private String normalizeDisplayName(String displayName, String fallback) {
        String normalized = displayName == null || displayName.isBlank() ? fallback : displayName.trim();
        if (normalized.length() > 255) {
            throw new InvalidRequestException("Display name is too long");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String normalized = description.trim();
        if (normalized.length() > 2000) {
            throw new InvalidRequestException("Description is too long");
        }
        return normalized;
    }

    public record UploadResult(AssetResponse response, boolean replayed) {

        static UploadResult created(AssetResponse response) {
            return new UploadResult(response, false);
        }

        static UploadResult replay(AssetResponse response) {
            return new UploadResult(response, true);
        }
    }
}
