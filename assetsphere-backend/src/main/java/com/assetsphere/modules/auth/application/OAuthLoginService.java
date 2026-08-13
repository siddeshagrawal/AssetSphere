package com.assetsphere.modules.auth.application;

import com.assetsphere.modules.auth.api.dto.response.AuthenticationResponse;
import com.assetsphere.modules.auth.domain.OAuthIdentity;
import com.assetsphere.modules.auth.domain.OAuthLoginTicket;
import com.assetsphere.modules.auth.domain.User;
import com.assetsphere.modules.auth.persistence.OAuthIdentityRepository;
import com.assetsphere.modules.auth.persistence.OAuthLoginTicketRepository;
import com.assetsphere.modules.auth.persistence.UserRepository;
import com.assetsphere.modules.common.exception.AuthenticationFailedException;
import com.assetsphere.modules.common.text.EmailNormalizer;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.workspace.api.WorkspaceFacade;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class OAuthLoginService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final OAuthIdentityRepository identities;
    private final OAuthLoginTicketRepository tickets;
    private final PasswordEncoder passwords;
    private final WorkspaceFacade workspaces;
    private final AuthenticationService authentication;
    private final ClockProvider clock;

    @Transactional
    public String completeGoogleLogin(Map<String, Object> attributes) {
        if (!Boolean.TRUE.equals(attributes.get("email_verified"))) throw new AuthenticationFailedException("Google email is not verified");
        String subject = required(attributes.get("sub"), "Google identity is invalid");
        String email = EmailNormalizer.normalize(required(attributes.get("email"), "Google email is unavailable"));
        String name = required(attributes.getOrDefault("name", email.substring(0, email.indexOf('@'))), "Google profile name is unavailable");
        var existingIdentity = identities.findByProviderAndProviderSubject("GOOGLE", subject).orElse(null);
        User user;
        if (existingIdentity != null) {
            user = users.findById(existingIdentity.getUserId()).orElseThrow(() -> new AuthenticationFailedException("Linked account is unavailable"));
            if (!user.getNormalizedEmail().equals(email)) throw new AuthenticationFailedException("Google identity email does not match linked account");
        } else {
            user = users.findByNormalizedEmail(email).orElse(null);
            if (user == null) {
                user = users.saveAndFlush(User.oauth(email, passwords.encode(UUID.randomUUID().toString()), name.trim()));
                workspaces.createPersonalWorkspace(user.getId(), user.getDisplayName());
            }
            identities.saveAndFlush(new OAuthIdentity(user.getId(), "GOOGLE", subject, clock.now()));
        }
        user.recordSuccessfulLogin(clock.now());
        String raw = token();
        tickets.save(new OAuthLoginTicket(user.getId(), hash(raw), clock.now()));
        return raw;
    }

    @Transactional
    public AuthenticationResponse exchange(String rawCode, String clientMetadata) {
        OAuthLoginTicket ticket = tickets.findByTicketHashForUpdate(hash(rawCode))
                .orElseThrow(() -> new AuthenticationFailedException("OAuth login code is invalid or expired"));
        ticket.consume(clock.now());
        return authentication.issueOAuthSession(ticket.getUserId(), clientMetadata);
    }

    private String token() { byte[] bytes = new byte[48]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private String required(Object value, String message) { if (!(value instanceof String text) || text.isBlank()) throw new AuthenticationFailedException(message); return text; }
}
