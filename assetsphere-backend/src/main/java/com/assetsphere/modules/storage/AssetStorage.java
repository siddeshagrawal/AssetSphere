package com.assetsphere.modules.storage;

import java.io.InputStream;

public interface AssetStorage {
    StoredAssetObject store(StoreAssetCommand command);
    void delete(String objectKey);

    record StoreAssetCommand(String objectKey, InputStream content, long contentLength, String mimeType) { }
    record StoredAssetObject(String objectKey) { }
}
