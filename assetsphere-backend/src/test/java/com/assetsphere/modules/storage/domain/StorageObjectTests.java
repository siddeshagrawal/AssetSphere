package com.assetsphere.modules.storage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StorageObjectTests {

    @Test
    void createsStorageObjectWithInitialReferenceCount() {
        StorageObject storageObject = StorageObject.create(
                UUID.randomUUID(), "checksum", "workspace/checksum", 10, "application/pdf"
        );

        assertThat(storageObject.getStorageProvider()).isEqualTo(StorageProvider.MINIO);
        assertThat(storageObject.getReferenceCount()).isEqualTo(1);
        assertThat(storageObject.getFileSize()).isEqualTo(10);
    }

    @Test
    void incrementsReferenceCountThroughControlledMethod() {
        StorageObject storageObject = StorageObject.create(
                UUID.randomUUID(), "checksum", "workspace/checksum", 10, "application/pdf"
        );

        storageObject.incrementReferenceCount();

        assertThat(storageObject.getReferenceCount()).isEqualTo(2);
    }

    @Test
    void rejectsNonPositiveFileSize() {
        assertThatThrownBy(() -> StorageObject.create(
                UUID.randomUUID(), "checksum", "workspace/checksum", 0, "application/pdf"
        )).isInstanceOf(BusinessRuleViolationException.class);
    }
}
