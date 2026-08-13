package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.intelligence.api.DocumentIntelligenceRequest;

record IntelligencePreparedWork(DocumentIntelligenceRequest request) {

    static IntelligencePreparedWork skipped() {
        return new IntelligencePreparedWork(null);
    }

    boolean shouldInvokeProvider() {
        return request != null;
    }
}
