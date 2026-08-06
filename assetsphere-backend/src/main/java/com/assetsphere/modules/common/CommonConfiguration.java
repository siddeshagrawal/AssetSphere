package com.assetsphere.modules.common;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CommonConfiguration {
    @Bean
    ClockProvider clockProvider() {
        return () -> Instant.now(Clock.systemUTC());
    }
}
