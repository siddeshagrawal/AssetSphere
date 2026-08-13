package com.assetsphere.modules.common.security;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface UserIdentityDirectory {

    Map<UUID, UserIdentity> findByIds(Collection<UUID> userIds);

    record UserIdentity(UUID userId, String displayName, String email) {
    }
}
