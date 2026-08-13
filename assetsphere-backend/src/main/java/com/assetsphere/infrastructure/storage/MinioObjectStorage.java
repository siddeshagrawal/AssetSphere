package com.assetsphere.infrastructure.storage;

import com.assetsphere.infrastructure.config.ApplicationProperties;
import com.assetsphere.modules.storage.api.AssetStorage;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.MakeBucketArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@ConditionalOnProperty(prefix = "assetsphere.storage.minio", name = "enabled", havingValue = "true")
class MinioObjectStorage implements AssetStorage {

    private final MinioClient minioClient;
    private final String bucket;
    private final boolean autoCreateBucket;

    MinioObjectStorage(ApplicationProperties properties) {
        var config = properties.getStorage().getMinio();
        this.minioClient = MinioClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKey(), config.getSecretKey())
                .build();
        this.bucket = config.getBucket();
        this.autoCreateBucket = config.isAutoCreateBucket();
    }

    @PostConstruct
    void createBucketIfMissing() {
        if (!autoCreateBucket) return;
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw new StorageAccessException("Unable to initialize object storage", exception);
        }
    }

    @Override
    public StoredAssetObject store(StoreAssetCommand storeAssetCommand) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(storeAssetCommand.objectKey())
                    .stream(storeAssetCommand.content(), storeAssetCommand.contentLength(), -1)
                    .contentType(storeAssetCommand.mimeType())
                    .build());
            return new StoredAssetObject(storeAssetCommand.objectKey());
        } catch (Exception exception) {
            throw new StorageAccessException("Unable to store object", exception);
        }
    }

    @Override
    public void copy(String sourceObjectKey, String targetObjectKey) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(targetObjectKey)
                    .source(CopySource.builder().bucket(bucket).object(sourceObjectKey).build())
                    .build());
        } catch (Exception exception) {
            throw new StorageAccessException("Unable to copy object", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new StorageAccessException("Unable to delete object", exception);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new StorageAccessException("Unable to open object", exception);
        }
    }
}
