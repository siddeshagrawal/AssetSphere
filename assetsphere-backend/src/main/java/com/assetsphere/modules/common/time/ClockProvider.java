package com.assetsphere.modules.common.time;

import java.time.Instant;

public interface ClockProvider {
    Instant now();
}
