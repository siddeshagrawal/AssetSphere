@org.springframework.modulith.ApplicationModule(
        displayName = "Storage",
        allowedDependencies = {
                "modules.common::exception",
                "modules.common::persistence"
        }
)
package com.assetsphere.modules.storage;
