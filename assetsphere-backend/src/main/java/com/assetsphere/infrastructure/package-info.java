@org.springframework.modulith.ApplicationModule(
        displayName = "Infrastructure",
        allowedDependencies = {
                "modules.common::persistence",
                "modules.common::exception",
                "modules.common::time",
                "modules.common::security",
                "modules.common::web",
                "modules.storage::api",
                "modules.asset::api",
                "modules.processing::api",
                "modules.intelligence::api",
                "modules.search::api",
                "modules.billing::api",
                "modules.auth::api",
                "modules.workspace::api"
        }
)
package com.assetsphere.infrastructure;
