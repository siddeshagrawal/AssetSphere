@org.springframework.modulith.ApplicationModule(
        displayName = "Authentication",
        allowedDependencies = {
                "modules.common::exception",
                "modules.common::persistence",
                "modules.common::security",
                "modules.common::text",
                "modules.common::time",
                "modules.common::web",
                "modules.audit::api",
                "modules.workspace::api"
        }
)
package com.assetsphere.modules.auth;
