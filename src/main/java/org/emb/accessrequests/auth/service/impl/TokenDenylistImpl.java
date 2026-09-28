package org.emb.accessrequests.auth.service.impl;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.emb.accessrequests.auth.service.LoginRateLimiter;
import org.emb.accessrequests.auth.service.TokenDenylist;
import org.springframework.stereotype.Component;

/**
 * Token ids revoked by logout, kept in memory until the token would have
 * expired anyway. Single-instance only, by design (like {@link LoginRateLimiter});
 * a restart forgets revocations, so keep the token lifetime short.
 */
@Component
public class TokenDenylistImpl implements TokenDenylist {

    private final ConcurrentHashMap<String, Instant> revoked = new ConcurrentHashMap<>();

    @Override
    public void revoke(String tokenId, Instant expiresAt) {
        Instant now = Instant.now();
        revoked.values().removeIf(expiry -> expiry.isBefore(now));
        if (tokenId != null && expiresAt != null && expiresAt.isAfter(now)) {
            revoked.put(tokenId, expiresAt);
        }
    }

    @Override
    public boolean isRevoked(String tokenId) {
        return tokenId != null && revoked.containsKey(tokenId);
    }
}
