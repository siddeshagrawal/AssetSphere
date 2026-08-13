package com.assetsphere.modules.storage.api;

import java.io.InputStream;

/**
 * Provider-neutral binary storage port used by Asset upload orchestration.
 */
public interface AssetStorage {

    StoredAssetObject store(StoreAssetCommand storeAssetCommand);

    void copy(String sourceObjectKey, String targetObjectKey);

    void delete(String objectKey);

    InputStream open(String objectKey);

    record StoreAssetCommand(String objectKey, InputStream content, long contentLength, String mimeType) {
    }

    record StoredAssetObject(String objectKey) {
    }
}
