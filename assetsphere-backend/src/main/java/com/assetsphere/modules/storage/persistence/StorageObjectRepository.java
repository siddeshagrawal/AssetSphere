package com.assetsphere.modules.storage.persistence;

import com.assetsphere.modules.storage.domain.StorageObject;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageObjectRepository extends JpaRepository<StorageObject, UUID> {

    Optional<StorageObject> findByWorkspaceIdAndChecksumSha256(UUID workspaceId, String checksumSha256);
}
