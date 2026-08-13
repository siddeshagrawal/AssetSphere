package com.assetsphere.modules.intelligence.api;

/** Provider-neutral port. Spring AI and provider SDK classes must not cross this boundary. */
public interface DocumentIntelligenceModel {

    DocumentIntelligenceResult analyze(DocumentIntelligenceRequest request);
}
