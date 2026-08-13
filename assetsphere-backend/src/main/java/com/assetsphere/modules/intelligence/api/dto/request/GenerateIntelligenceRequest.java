package com.assetsphere.modules.intelligence.api.dto.request;

import jakarta.validation.constraints.Size;

public record GenerateIntelligenceRequest(@Size(max = 128) String modelId) { }
