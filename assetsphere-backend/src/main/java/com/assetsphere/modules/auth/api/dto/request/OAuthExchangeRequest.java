package com.assetsphere.modules.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuthExchangeRequest(@NotBlank @Size(max = 256) String code) { }
