package org.emb.accessrequests.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import javax.crypto.SecretKey;

import org.emb.accessrequests.auth.dto.SessionUser;
import org.emb.accessrequests.auth.service.impl.JwtServiceImpl;
import org.emb.accessrequests.auth.service.impl.TokenDenylistImpl;
import org.emb.accessrequests.config.AppProperties;
import org.emb.accessrequests.config.JwtConfig;
import org.emb.accessrequests.user.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceTest {

    private static final SessionUser ALICE = new SessionUser(4, "alice", "Alice Anderson", Role.USER, 2L);

    private final JwtConfig config = new JwtConfig();
    private final TokenDenylist denylist = new TokenDenylistImpl();

    private AppProperties properties(String secret) {
        return new AppProperties(10240, new AppProperties.LoginRateLimit(5, Duration.ofMinutes(15)),
                new AppProperties.Jwt(secret, Duration.ofHours(2)));
    }

    @Test
    void anIssuedTokenDecodesToTheUser_untilItIsRevoked() {
        AppProperties properties = properties("a-test-secret-that-is-at-least-32-bytes");
        SecretKey key = config.jwtSigningKey(properties);
        JwtService jwtService = new JwtServiceImpl(config.jwtEncoder(key), properties);
        JwtDecoder decoder = config.jwtDecoder(key, denylist);

        Jwt issued = jwtService.issue(ALICE);
        Jwt decoded = decoder.decode(issued.getTokenValue());

        assertThat(decoded.getSubject()).isEqualTo("4");
        assertThat(decoded.getId()).isNotBlank();
        assertThat(decoded.getExpiresAt()).isAfter(decoded.getIssuedAt());

        denylist.revoke(decoded.getId(), decoded.getExpiresAt());
        assertThatThrownBy(() -> decoder.decode(issued.getTokenValue())).isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        AppProperties ours = properties("a-test-secret-that-is-at-least-32-bytes");
        AppProperties theirs = properties("another-secret-that-is-also-32-bytes-long");
        String foreign = new JwtServiceImpl(config.jwtEncoder(config.jwtSigningKey(theirs)), theirs)
                .issue(ALICE).getTokenValue();

        JwtDecoder decoder = config.jwtDecoder(config.jwtSigningKey(ours), denylist);

        assertThatThrownBy(() -> decoder.decode(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void aShortSecretFailsFast() {
        assertThatThrownBy(() -> config.jwtSigningKey(properties("too-short")))
                .isInstanceOf(IllegalStateException.class);
    }
}
