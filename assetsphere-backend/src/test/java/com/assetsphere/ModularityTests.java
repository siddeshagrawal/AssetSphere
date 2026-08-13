package com.assetsphere;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(AssetSphereApplication.class).verify();
    }

    @Test
    void detectsAllExplicitApplicationModules() {
        ApplicationModules modules = ApplicationModules.of(AssetSphereApplication.class);

        Set<String> moduleNames = modules.stream()
                .map(module -> module.getIdentifier().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(moduleNames).containsExactlyInAnyOrder(
                "infrastructure",
                "modules.asset",
                "modules.audit",
                "modules.auth",
                "modules.billing",
                "modules.common",
                "modules.intelligence",
                "modules.processing",
                "modules.search",
                "modules.storage",
                "modules.workspace"
        );
    }

}
