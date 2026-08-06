package com.assetsphere.modules.common;

import java.time.Instant;

public interface ClockProvider {
    Instant now();
}
