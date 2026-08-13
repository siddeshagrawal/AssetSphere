package com.assetsphere.modules.auth.application;

import com.assetsphere.modules.auth.persistence.UserRepository;
import com.assetsphere.modules.common.security.UserIdentityDirectory;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class UserIdentityDirectoryAdapter implements UserIdentityDirectory {

    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserIdentity> findByIds(Collection<UUID> userIds) {
        return users.findAllById(userIds).stream()
                .map(user -> new UserIdentity(user.getId(), user.getDisplayName(), user.getNormalizedEmail()))
                .collect(Collectors.toMap(UserIdentity::userId, Function.identity()));
    }
}
