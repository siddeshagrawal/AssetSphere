package com.assetsphere.infrastructure.storage;

import com.assetsphere.infrastructure.config.ApplicationProperties;
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
class MinioObjectStorage implements ObjectStorage {
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
    public void put(String key, InputStream content, long size, String contentType) {
        client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(content, size, -1).contentType(contentType).build());
    }

    @Override
    @SneakyThrows
    public InputStream get(String key) {
        return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
    }

    @Override
    @SneakyThrows
    public void delete(String key) {
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
    }
}
