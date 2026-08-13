package com.assetsphere.modules.search.api;

import java.util.List;
import java.util.UUID;

public interface WorkspaceSearchEvidenceRetriever {

    List<WorkspaceSearchEvidence> retrieve(UUID workspaceId, String query, int limit);
}
