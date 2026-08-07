package com.assetsphere.infrastructure.storage;

import com.assetsphere.infrastructure.config.ApplicationProperties;
import com.assetsphere.modules.storage.api.AssetStorage;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "assetsphere.storage.minio", name = "enabled", havingValue = "true")
class MinioObjectStorage implements AssetStorage {
    private final MinioClient client;
    private final String bucket;

    MinioObjectStorage(ApplicationProperties properties) {
        var config = properties.getStorage().getMinio();
        this.client = MinioClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKey(), config.getSecretKey())
                .build();
        this.bucket = config.getBucket();
    }

    @PostConstruct
    void createBucketIfMissing() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw new StorageAccessException("Unable to initialize object storage", exception);
        }
    }

    @Override
    public StoredAssetObject store(StoreAssetCommand command) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(command.objectKey())
                    .stream(command.content(), command.contentLength(), -1)
                    .contentType(command.mimeType())
                    .build());
            return new StoredAssetObject(command.objectKey());
        } catch (Exception exception) {
            throw new StorageAccessException("Unable to store object", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new StorageAccessException("Unable to delete object", exception);
        }
    }
}
