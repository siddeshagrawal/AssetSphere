package com.assetsphere.modules.auth.persistence;
import com.assetsphere.modules.auth.domain.User;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByNormalizedEmail(String normalizedEmail);
}
