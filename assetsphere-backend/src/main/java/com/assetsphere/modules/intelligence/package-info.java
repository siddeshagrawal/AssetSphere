@org.springframework.modulith.ApplicationModule(
        displayName = "Intelligence",
        allowedDependencies = {
                "modules.common::exception",
                "modules.common::persistence",
                "modules.common::security",
                "modules.common::time",
                "modules.common::web",
                "modules.asset::api",
                "modules.processing::api",
                "modules.search::api",
                "modules.workspace::api",
                "modules.audit::api",
                "modules.billing::api"
        }
)
package com.assetsphere.modules.intelligence;
