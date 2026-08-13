@org.springframework.modulith.ApplicationModule(
        displayName = "Asset",
        allowedDependencies = {
                "modules.common::exception",
                "modules.common::persistence",
                "modules.common::security",
                "modules.common::time",
                "modules.common::web",
                "modules.workspace::api",
                "modules.storage::api",
                "modules.audit::api",
                "modules.billing::api"
        }
)
package com.assetsphere.modules.asset;
