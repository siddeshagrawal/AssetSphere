@org.springframework.modulith.ApplicationModule(
        displayName = "Infrastructure",
        allowedDependencies = {
                "modules.common::persistence",
                "modules.common::time",
                "modules.common::security",
                "modules.common::web",
                "modules.storage::api"
        }
)
package com.assetsphere.infrastructure;
