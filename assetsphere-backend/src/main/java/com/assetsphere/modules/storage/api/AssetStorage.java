package com.assetsphere.modules.storage.api;

import java.io.InputStream;

/** Provider-neutral binary storage port used by future Asset upload orchestration. */
public interface AssetStorage {

    StoredAssetObject store(StoreAssetCommand command);

    void delete(String objectKey);

    record StoreAssetCommand(String objectKey, InputStream content, long contentLength, String mimeType) {
    }

    record StoredAssetObject(String objectKey) {
    }
}
