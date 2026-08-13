package com.assetsphere.modules.asset.persistence;

import com.assetsphere.modules.asset.domain.Asset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    Optional<Asset> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select asset from Asset asset where asset.id = :assetId and asset.workspaceId = :workspaceId")
    Optional<Asset> findForUpdate(@Param("assetId") UUID assetId, @Param("workspaceId") UUID workspaceId);

    Page<Asset> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId, Pageable pageable);

    long countByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);
}
