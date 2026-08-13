@org.springframework.modulith.ApplicationModule(
        displayName = "Search",
        allowedDependencies = {
                "modules.common::exception",
                "modules.common::persistence",
                "modules.common::security",
                "modules.common::time",
                "modules.common::web",
                "modules.workspace::api",
                "modules.asset::api"
        }
)
package com.assetsphere.modules.search;
