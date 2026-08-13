@org.springframework.modulith.ApplicationModule(
        displayName = "Processing",
        allowedDependencies = {
                "modules.common::exception",
                "modules.common::persistence",
                "modules.common::time",
                "modules.asset::api",
                "modules.storage::api",
                "modules.search::api"
        }
)
package com.assetsphere.modules.processing;
