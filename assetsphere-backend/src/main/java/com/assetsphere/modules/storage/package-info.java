@org.springframework.modulith.ApplicationModule(
        displayName = "Storage",
        allowedDependencies = {
                "modules.common::exception",
                "modules.common::persistence",
                "modules.common::time"
        }
)
package com.assetsphere.modules.storage;
