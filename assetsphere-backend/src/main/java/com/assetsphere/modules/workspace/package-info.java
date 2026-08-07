@org.springframework.modulith.ApplicationModule(
        displayName = "Workspace",
        allowedDependencies = {
                "modules.common::exception",
                "modules.common::persistence",
                "modules.common::security",
                "modules.common::text",
                "modules.common::time",
                "modules.common::web",
                "modules.audit::api"
        }
)
package com.assetsphere.modules.workspace;
