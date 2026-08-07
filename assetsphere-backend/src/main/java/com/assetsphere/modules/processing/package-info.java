@org.springframework.modulith.ApplicationModule(
        displayName = "Processing",
        allowedDependencies = {
                "modules.common::exception",
                "modules.common::persistence"
        }
)
package com.assetsphere.modules.processing;
