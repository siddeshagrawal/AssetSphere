@org.springframework.modulith.ApplicationModule(
        displayName = "Billing",
        allowedDependencies = {
                "modules.common::exception",
                "modules.common::persistence",
                "modules.common::security",
                "modules.common::time",
                "modules.common::web",
                "modules.workspace::api"
        }
)
package com.assetsphere.modules.billing;
