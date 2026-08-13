package com.assetsphere.modules.auth.persistence;

import com.assetsphere.modules.auth.domain.OAuthIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, UUID> {
    Optional<OAuthIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
