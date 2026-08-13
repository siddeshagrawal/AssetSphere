package com.assetsphere.modules.intelligence.api;

import java.util.List;

public record WorkspaceInsightResult(String summary, List<Item> items) {
    public record Item(String title, String secondary, String detail, String severity, List<String> sourceIds) { }
}
