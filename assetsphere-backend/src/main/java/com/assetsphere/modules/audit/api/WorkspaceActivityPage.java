package com.assetsphere.modules.audit.api;

import java.util.List;

public record WorkspaceActivityPage(List<WorkspaceActivityResponse> content, int page, int size,
                                    long totalElements, int totalPages) { }
