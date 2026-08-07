package com.assetsphere.infrastructure.storage;

import com.assetsphere.infrastructure.config.ApplicationProperties;
import com.assetsphere.modules.storage.AssetStorage;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;

import java.io.InputStream;
import jakarta.annotation.PostConstruct;

import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "assetsphere.storage.minio", name = "enabled", havingValue = "true")
class MinioObjectStorage implements AssetStorage {
    private final MinioClient client;
    private final String bucket;

    MinioObjectStorage(ApplicationProperties properties) {
        var config = properties.getStorage().getMinio();
        this.client = MinioClient.builder().endpoint(config.getEndpoint()).credentials(config.getAccessKey(), config.getSecretKey()).build();
        this.bucket = config.getBucket();
    }
    @PostConstruct @SneakyThrows
    void createBucketIfMissing() {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    @Override
    @SneakyThrows
    public StoredAssetObject store(StoreAssetCommand command) {
        client.putObject(PutObjectArgs.builder().bucket(bucket).object(command.objectKey()).stream(command.content(), command.contentLength(), -1).contentType(command.mimeType()).build());
        return new StoredAssetObject(command.objectKey());
    }

    @Override
    @SneakyThrows
    public void delete(String objectKey) {
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    }
}
